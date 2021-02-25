#!/usr/local/bin/groovy
@GrabResolver(name = 'jcenter', root = 'https://jcenter.bintray.com/')
@Grab('org.codehaus.gpars:gpars:0.9')
@Grab('org.codehaus.groovy.modules.http-builder:http-builder:0.7.2')
@Grab('commons-io:commons-io:1.2')
import groovyx.gpars.GParsPool
import org.apache.commons.io.FileUtils
import groovyx.net.http.RESTClient
// Run the generator
new GenerateRPM().init(args)


class GenerateRPM extends Generator {
    public static final String OUTPUT_PREFIX="##RPM##"
    public static final String ADD_PREFIX="ADD"
    // User input
    def artifactoryUrl, artifactoryUser, artifactoryPassword, repoKey, rootDir, packagePrefix, packageRelease,
            packageProperties
    Integer numOfThreads, numOfPackages, minSize, maxSize
    synchronized def passed = true

    /**
     * Generates rpm packages and deploys them Artifactory
     */
    boolean generate() {
        Random random = new Random()
        println """ What we are going to do? We are going to build  $numOfPackages packages. 
These packages will be deployed to the repo $repoKey in the directory $rootDir. 
They will range in size from $minSize to $maxSize bytes and have the format $packagePrefix#.rpm.
"""

        // Login
        ['jfrog', 'rt', 'c', "--interactive=false", "--url=${artifactoryUrl}", "--user=${artifactoryUser}", "--password=${artifactoryPassword}", 'art'].execute()
        // Creates and uploads files in batches
        GParsPool.withPool numOfThreads, {
            0.step numOfPackages, numOfThreads, {
                def batch_start = it
                File batchDir = new File("tmp/generator/$batch_start")
                batchDir.mkdirs()
                // A batch of files is equal to the number of threads the user has asked for
                (batch_start..(Math.min(batch_start+numOfThreads, numOfPackages) - 1)).eachParallel { id ->
                    File pkgBaseDir = new File("tmp/generator/$batch_start/$id")
                    File pkgFileDir = new File("tmp/generator/$batch_start/$id/$packagePrefix")
                    pkgFileDir.mkdirs()
                    ['cp', '-R', 'root/rpmbuild', "tmp/generator/$batch_start/$id/"].execute ( ).waitForOrKill ( 5000 )
                    File addFile = new File(pkgFileDir, "$packagePrefix$id")
                    int fileSize = (maxSize == minSize) ? minSize : Math.abs(random.nextLong() % (maxSize - minSize)) + minSize
                    HelperTools.createBinFile(addFile, fileSize)
                    ['tar', '-zcf', "rpmbuild/SOURCES/${packagePrefix}.tar.gz", "${packagePrefix}/"].execute (null, pkgBaseDir).waitForOrKill ( 15000 )
                    ['sed', '-i', "s/{i}/$id/g", "rpmbuild/SPECS/rpmpackage.spec"].execute (null, pkgBaseDir).waitForOrKill ( 15000 )
                    ['sed', '-i', "s/{j}/$packageRelease/g",  "rpmbuild/SPECS/rpmpackage.spec"].execute (null, pkgBaseDir).waitForOrKill ( 15000 )
                    ['sed', '-i', "s/{NAME}/$packagePrefix/g", "rpmbuild/SPECS/rpmpackage.spec"].execute (null, pkgBaseDir).waitForOrKill ( 15000 )
                    ['rpmbuild', '-v', '-bb', "rpmbuild/SPECS/rpmpackage.spec"].execute (null, pkgBaseDir).waitForOrKill ( 35000 )
                    File rpmFile = new File("tmp/generator/$batch_start/$id/rpmbuild/RPMS/x86_64/${packagePrefix}-${id}-${packageRelease}.x86_64.rpm")
                    println("$OUTPUT_PREFIX $ADD_PREFIX $repoKey/$rootDir/${packagePrefix}-${id}-${packageRelease}.x86_64.rpm ${HelperTools.getFileSha1(rpmFile)}")
                }
                // Upload the batch of files
                long buildNumber = System.currentTimeMillis()
                String cmd = "jfrog rt upload " +
                        "--server-id=art " +
                        "--flat=true --threads=${numOfThreads} " +
                        "--build-name=dummy-project --build-number=${buildNumber} --props=${packageProperties} " +
                        "${batchDir}/*.rpm " +
                        "$repoKey/$rootDir/"
                println cmd
                passed &= HelperTools.executeCommandAndPrint(cmd) == 0 ? true : false
                FileUtils.deleteDirectory(batchDir)
            }
        }
        return passed
    }

    /**
     * Reads the output of the generate method and verifies that the data exists
     * @param filePath The output of the generator method (stored in a file)
     * @return True if the verification succeeded
     */
    boolean verify(def filePath) {
        def toVerify=[]
        // Create a map of artifact path and sha1s that we will use to verify
        (new File(filePath)).eachLine { String line ->
            if (line.startsWith("$OUTPUT_PREFIX $ADD_PREFIX ")) {
                toVerify << ["name": line.split()[2], "sha1": line.split()[3]]
            }
        }

        GParsPool.withPool numOfThreads, {
            toVerify.eachParallel {
                // Not thread safe so build one per thread
                RESTClient rc = new RESTClient(artifactoryUrl)
                def base64 = "${artifactoryUser}:${artifactoryPassword}".bytes.encodeBase64().toString()
                rc.setHeaders([Authorization: "Basic ${base64}"])
                try {
                    def response = rc.head(path: "${artifactoryUrl}/${it.name}")
                    def headers = response.getHeaders()
                    if (headers && headers["X-Checksum-Sha1"]) {
                        def artSha1 = headers["X-Checksum-Sha1"].getValue()
                        if (artSha1 != it.sha1)
                        {
                            System.err.println("Expected file ${it.name} with checksum ${it.sha1} " +
                                    "but it had sha1 ${artSha1}.")
                            passed = false
                        }
                    } else {
                        System.err.println("Expected file ${it.name} with checksum ${it.sha1} but did not find it.")
                        passed = false
                    }
                } catch (Exception e) {
                    System.err.println("Expected file ${it.name} with checksum ${it.sha1} but did not find it. " +
                            "Exception:  ${e.getMessage()}")
                    passed = false
                }
            }
        }
        return passed
    }

    /**
     * Reads the output of the generate method and cleans up
     * @param filePath The output of the generator method (stored in a file)
     * @return True if the cleanup succeeded
     */
    boolean cleanup(def filePath) {
        ['jfrog', 'rt', 'c', "--url=${artifactoryUrl}", "--user=${artifactoryUser}", "--password=${artifactoryPassword}", 'art'].execute ().waitForOrKill (15000)
        def toDelete=[]
        // Deletes the files created by this tool
        (new File(filePath)).eachLine { String line ->
            if (line.startsWith("$OUTPUT_PREFIX $ADD_PREFIX ")) {
                toDelete << line.split()[2]
            }
        }
        // Delete in batches
        GParsPool.withPool numOfThreads, {
            toDelete.eachParallel {
                String cmd = "jfrog rt delete --server-id=art --quiet $it"
                passed &= HelperTools.executeCommandAndPrint(cmd) == 0 ? true : false
            }
        }
        return passed
    }

    /**
     * Collects and verified the user provided input, transforming it as needed and erroring out if needed
     */
    void getInput() {
        // Load in the values from the user provided input
        artifactoryUrl = userInput.getUserInput("artifactory.url")
        if ( artifactoryUrl.endsWith ( '/' ) )
            artifactoryUrl = artifactoryUrl.substring (0, artifactoryUrl.length ( ) - 2 )
        artifactoryUser = userInput.getUserInput("artifactory.user")
        artifactoryPassword = userInput.getUserInput("artifactory.password")
        repoKey = userInput.getUserInput("artifactory.repo")
        numOfPackages = userInput.getUserInput("package.number") as Integer
        rootDir = userInput.getUserInput("package.root.dir")
        if (rootDir.startsWith('/'))
            rootDir = rootDir.substring(1)
        if ( rootDir.endsWith ( '/' ) )
            rootDir = rootDir.substring (0, rootDir.length ( ) - 2 )
        minSize = userInput.getUserInput("package.size.min") as Integer
        maxSize = userInput.getUserInput("package.size.max") as Integer
        packagePrefix = userInput.getUserInput("package.name.prefix")
        packageRelease = userInput.getUserInput("package.release")
        packageProperties = userInput.getUserInput("package.properties")
        numOfThreads = userInput.getUserInput("generator.threads") as Integer
        // TODO: Verify input
    }

}
