#!/usr/local/bin/groovy
@GrabResolver(name = 'jcenter', root = 'https://jcenter.bintray.com/')
@Grab('org.codehaus.gpars:gpars:0.9')
@Grab('org.codehaus.groovy.modules.http-builder:http-builder:0.7.2')
@Grab('commons-io:commons-io:1.2')
import groovyx.gpars.GParsPool
import org.apache.commons.io.FileUtils
import groovyx.net.http.RESTClient
// Run the generator
new GenerateDocker().init(args)


class GenerateDocker extends Generator {
    public static final String OUTPUT_PREFIX="##DOCKER##"
    public static final String ADD_PREFIX="ADD"
    // User input
    def artifactoryRegistry, artifactoryUrl, artifactoryUser, artifactoryPassword, repoKey, packageName,
            packageProperties
    Integer numOfThreads, numOfPackages, minSize, maxSize
    synchronized def passed = true

    /**
     * Generates docker images and pushes them to Artifactory.
     */
    boolean generate() {
        Random random = new Random()
        println """ What we are going to do? We are going to build  $numOfPackages docker image(s). 
These docker image(s) will be pushed to the docker repo ${repoKey} in Artifactory $artifactoryUrl. 
"""
        // Login
        ['docker', 'login', "${ artifactoryRegistry }", "--username=${ artifactoryUser }", "--password=${ artifactoryPassword }"].execute ( ).waitForOrKill ( 15000 )

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
                    ['cp', "root/TempDockerfile", "tmp/generator/$batch_start/$id/Dockerfile"].execute ( ).waitForOrKill ( 15000 )
                    File pkgBaseDir = new File("tmp/generator/$batch_start/$id")
                    File addFile = new File("tmp/generator/$batch_start/$id", "${packageName}${id}.dat")
                    int fileSize = (maxSize == minSize) ? minSize : Math.abs(random.nextLong() % (maxSize - minSize)) + minSize
                    HelperTools.createBinFile(addFile, fileSize)
                    ['sed', '-i', "s/{{GENERATED.DAT}}/${packageName}${id}.dat/g", "Dockerfile"].execute (null, pkgBaseDir).waitForOrKill ( 15000 )
                    ['docker', 'build', '-t', "${artifactoryRegistry}/${packageName}:1.${id}", '.'].execute (null, pkgBaseDir).waitForOrKill ( 35000 ) == 0
                    // Push generated docker image to Artifactory.
                    try {
                        def sha256digest = dockerPushCmdExec("${artifactoryRegistry}/${packageName}:1.${id}")
                        if (sha256digest != "") {
                            println("$OUTPUT_PREFIX $ADD_PREFIX ${packageName}/1.${id} ${sha256digest}")
                        }
                    } catch (Exception e) {
                        System.err.println("Docker image push to Artifactory registry failed. " +
                                "Exception:  ${e.getMessage()}")
                    }
                    // Delete generated docker image.
                    ['docker', 'rmi', "${artifactoryRegistry}/${packageName}:1.${id}"].execute(null, pkgBaseDir).waitForOrKill(35000) == 0
                    RESTClient rc = new RESTClient()
                    def base64 = "${artifactoryUser}:${artifactoryPassword}".bytes.encodeBase64().toString()
                    rc.setHeaders([Authorization: "Basic ${base64}"])
                    try {
                        rc.put(uri: "${artifactoryUrl}/api/storage/${repoKey}/${packageName}/1.${id}?properties=${packageProperties}")
                    } catch (Exception e) {
                        System.err.println("Update properties API call failed for ${repoKey}/${packageName}/1.${id} " +
                                "Exception:  ${e.getMessage()}")
                    }
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
                toVerify << ["name": line.split()[2], "sha256": line.split()[3]]
            }
        }

        GParsPool.withPool numOfThreads, {
            toVerify.eachParallel {
                // Not thread safe so build one per thread
                RESTClient rc = new RESTClient(artifactoryUrl)
                def base64 = "${artifactoryUser}:${artifactoryPassword}".bytes.encodeBase64().toString()
                rc.setHeaders([Authorization: "Basic ${base64}"])
                try {
                    def response = rc.head(path: "artifactory/${repoKey}/${it.name}/manifest.json")
                    def headers = response.getHeaders()
                    if (headers && headers["X-Checksum-Sha256"]) {
                        def artSha256 = headers["X-Checksum-Sha256"].getValue()
                        if (artSha256 != it.sha256)
                        {
                            System.err.println("Expected image ${it.name} with checksum ${it.sha256} " +
                                    "but it had sha256 ${artSha256}.")
                            passed = false
                        }
                    } else {
                        System.err.println("Expected image ${it.name} with checksum ${it.sha256} but did not find it.")
                        passed = false
                    }
                } catch (Exception e) {
                    System.err.println("Expected image ${it.name} with checksum ${it.sha256} but did not find it. " +
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
                toDelete << ["name": line.split()[2], "sha256": line.split()[3]]
            }
        }

        GParsPool.withPool numOfThreads, {
            toDelete.eachParallel {
                // Not thread safe so build one per thread
                RESTClient rc = new RESTClient(artifactoryUrl)
                def base64 = "${artifactoryUser}:${artifactoryPassword}".bytes.encodeBase64().toString()
                rc.setHeaders([Authorization: "Basic ${base64}"])
                try {
                    rc.delete(path: "artifactory/${repoKey}/${it.name}")
                } catch (Exception e) {
                    System.err.println("Artifactory delete operation failed for ${repoKey}/${it.name} " +
                            "Exception:  ${e.getMessage()}")
                    passed = false
                }
            }
        }
        return passed
    }

    /**
     * Run docker push command and return SHA256 the digest string.
     * @param dockerImageAndTag The docker image name with tag reference.
     * @return SHA256 digest string for the pushed docker image.
     */
    String dockerPushCmdExec(def dockerImageAndTag) {
        def dockerPushCmd = "docker push ${dockerImageAndTag}"
        ProcessBuilder builder = new ProcessBuilder(dockerPushCmd.split(' '))
        builder.redirectErrorStream(true)
        Process process = builder.start()
        InputStream stdout = process.getInputStream()
        BufferedReader reader = new BufferedReader(new InputStreamReader(stdout))
        String line
        def sha256digest = ""
        while ((line = reader.readLine()) != null) {
            if (line =~ /digest: sha256:/) {
                def m = line =~ /digest: sha256:(.+?) size:/
                sha256digest = m.find()?m.group(1):""
            }
        }
        process.waitFor()
        return sha256digest
    }

    /**
     * Collects and verified the user provided input, transforming it as needed and erroring out if needed
     */
    void getInput() {
        // Load in the values from the user provided input
        artifactoryRegistry = userInput.getUserInput("artifactory.registry")
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
