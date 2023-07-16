#!/usr/local/bin/groovy
//@GrabResolver(name='restlet.org', root='http://maven.restlet.org')
@Grab('org.codehaus.gpars:gpars:1.2.1')
@Grab('org.codehaus.groovy.modules.http-builder:http-builder')
@Grab('commons-io:commons-io:2.11.0')
import groovyx.gpars.GParsPool
import org.apache.commons.io.FileUtils
import groovyx.net.http.RESTClient
// Run the generator
new GenerateNPM().init(args)


class GenerateNPM extends Generator {
    public static final String OUTPUT_PREFIX="##NPM##"
    public static final String ADD_PREFIX="ADD"
    // User input
    def artifactoryUrl, artifactoryUser, artifactoryPassword, repoKey, packageName,
        packageProperties
    Integer numOfThreads, numOfPackages, minSize, maxSize
    synchronized def passed = true

    /**
     * Generates npm packages and deploys them Artifactory
     */
    boolean generate() {
        Random random = new Random()
        println """ What we are going to do? We are going to build  $numOfPackages npm packages. 
These packages will be deployed to the repo $repoKey. 
"""

        // CLI Login
        String login_cmd = "jfrog rt c " + "--interactive=false " + "--url=${artifactoryUrl} " + "--user=${artifactoryUser} " + "--password=${artifactoryPassword} " + "art "
        println login_cmd
        passed &= HelperTools.executeCommandAndPrint(login_cmd) == 0 ? true : false
        // Login setup
//        ['root/npmSetup.sh'].execute ().waitForOrKill ( 15000 )

        if (packageName != "generated") {
            ['mv', 'root/generated', "root/${packageName}"].execute ().waitForOrKill ( 15000 )
        }

        // Creates and uploads files in batches
        GParsPool.withPool numOfThreads, {
            0.step numOfPackages, numOfThreads, {
                def batch_start = it
                File batchDir = new File("tmp/generator/$batch_start")
                batchDir.mkdirs()
                // A batch of files is equal to the number of threads the user has asked for
                (batch_start..(Math.min(batch_start+numOfThreads, numOfPackages) - 1)).eachParallel { id ->
                    File iterDir = new File("tmp/generator/$batch_start/$id")
                    iterDir.mkdirs()
                    ['cp', '-R', "/root/${packageName}", "/tmp/generator/$batch_start/$id/${packageName}"].execute ( ).waitForOrKill ( 15000 )
                    File pkgBaseDir = new File("/tmp/generator/$batch_start/$id/${packageName}")
                    File addFile = new File(pkgBaseDir, "${packageName}.dat")
                    int fileSize = (maxSize == minSize) ? minSize : Math.abs(random.nextLong() % (maxSize - minSize)) + minSize
                    HelperTools.createBinFile(addFile, fileSize)
                    ['sed', '-i', "s/{{VERSION}}/1.$id.0/g", "package.json"].execute (null, pkgBaseDir).waitForOrKill ( 15000 )
                    ['sed', '-i', "s/\"generated\"/\"${packageName}\"/g", "package.json"].execute (null, pkgBaseDir).waitForOrKill ( 15000 )
                    ['sed', '-i', "s/generated.dat/${packageName}.dat/g", "package.json"].execute (null, pkgBaseDir).waitForOrKill ( 15000 )
//                    ['npm', 'install', "${packageName}${id}"].execute (null, iterDir).waitForOrKill ( 25000 )
                    ['npm', 'pack'].execute (null, pkgBaseDir).waitForOrKill ( 36000000 )
                    File npmFile = new File("/tmp/generator/$batch_start/$id/${packageName}/${packageName}-1.$id.0.tgz")
                    println(npmFile.exists())
                    println(iterDir.listFiles())
                    println("$OUTPUT_PREFIX $ADD_PREFIX $repoKey/${packageName}/-/${packageName}-1.${id}.0.tgz")
//                    RESTClient rc = new RESTClient()
//                    def base64 = "${artifactoryUser}:${artifactoryPassword}".bytes.encodeBase64().toString()
//                    rc.setHeaders([Authorization: "Basic ${base64}"])
//                    try {
//                        rc.put(uri: "${artifactoryUrl}/api/storage/$repoKey/${packageName}${id}?properties=${packageProperties}")
//                    } catch (Exception e) {
//                        System.err.println("Update properties API call failed for $repoKey/${packageName}${id} " +
//                                "Exception:  ${e.getMessage()}")
//                    }
                    long buildNumber = System.currentTimeMillis()
                    String upload_cmd = "jfrog rt upload " +
                            "--server-id=art " +
                            "--flat=true --threads=${numOfThreads} " +
                            "--build-name=dummy-project --build-number=${buildNumber} --props=${packageProperties} " +
                            "/${batchDir}/*.tgz " +
                            "$repoKey/"
                    println upload_cmd
                    passed &= HelperTools.executeCommandAndPrint(upload_cmd) == 0 ? true : false
                }
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
        def toDelete=[]
        // Create a map of artifact path and sha1s that we will use to delete.
        (new File(filePath)).eachLine { String line ->
            if (line.startsWith("$OUTPUT_PREFIX $ADD_PREFIX ")) {
                toDelete << ["name": line.split()[2], "sha1": line.split()[3]]
            }
        }

        GParsPool.withPool numOfThreads, {
            toDelete.eachParallel {
                // Not thread safe so build one per thread
                RESTClient rc = new RESTClient(artifactoryUrl)
                def base64 = "${artifactoryUser}:${artifactoryPassword}".bytes.encodeBase64().toString()
                rc.setHeaders([Authorization: "Basic ${base64}"])
                try {
                    def matchpkg = it.name =~ /(.+)\/-/
                    matchpkg.find()?rc.delete(path: "artifactory/"+ matchpkg.group(1)):""
                } catch (Exception e) {
                    System.err.println("Artifactory delete operation failed for ${it.name}. " +
                            "Exception:  ${e.getMessage()}")
                    passed = false
                }
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
        minSize = userInput.getUserInput("package.size.min") as Integer
        maxSize = userInput.getUserInput("package.size.max") as Integer
        packageName = userInput.getUserInput("package.name")
        packageProperties = userInput.getUserInput("package.properties")
        numOfThreads = userInput.getUserInput("generator.threads") as Integer
    }

}
