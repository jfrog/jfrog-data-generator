#!/usr/local/bin/groovy
@GrabResolver(name = 'jcenter', root = 'https://jcenter.bintray.com/')
@Grab('org.codehaus.gpars:gpars:0.9')
@Grab('org.codehaus.groovy.modules.http-builder:http-builder:0.7.2')
@Grab('commons-io:commons-io:1.2')
import groovyx.gpars.GParsPool
import org.apache.commons.io.FileUtils
import java.security.SecureRandom
import groovyx.net.http.RESTClient

// Run the generator
new GeneratePHP().init(args)

class GeneratePHP extends Generator {
    public static final String OUTPUT_PREFIX = "##PHP##"
    public static final String ADD_PREFIX = "ADD"
    public static final int NUM_OF_WORKERS = 8
    // User input
    def artifactoryUrl, artifactoryUser, artifactoryPassword, repoKey, packagesAmount, packageNumberStart, maxFileSize, minFileSize
    synchronized def passed = true

    /**
     * Generates php packages and deploys them to Artifactory
     */
    boolean generate() {
        SecureRandom random = new SecureRandom()
        String artifactsDir = "artifacts"
        File outDir = new File("generated")
        outDir.mkdirs()
        println """ What we are going to do?
        We are going to build  $packagesAmount  packages"""

        ['jfrog', 'rt', 'c', 'art', "--url=${artifactoryUrl}", "--user=${artifactoryUser}", "--password=${artifactoryPassword}"].execute().waitForOrKill(15000)

        GParsPool.withPool 12, {
            (packageNumberStart..packagesAmount).eachParallel {
                String artifactName = HelperTools.generateString(9)
                println "Creating artifacts ${artifactName}"
                File artifactFolder = new File("$artifactsDir/$artifactName")
                artifactFolder.mkdirs()
                sleep(1)
                int fileSize = (minFileSize == maxFileSize) ? minFileSize : Math.abs(random.nextLong() % (maxFileSize - minFileSize)) + minFileSize
                String extraFileName = "${artifactName}.php"
                File addFile = new File(artifactFolder, extraFileName)
                HelperTools.createBinFile(addFile, fileSize)
                println("$OUTPUT_PREFIX $ADD_PREFIX $repoKey/${artifactFolder}/${extraFileName} ${HelperTools.getFileSha1(addFile)}")

                // composer.json
                String composerName = "composer.json"
                File composerFile = new File(artifactFolder, composerName)
                composerFile << generateComposerJson(artifactName as String)
                println("$OUTPUT_PREFIX $ADD_PREFIX $repoKey/${artifactFolder}/${composerName} ${HelperTools.getFileSha1(composerFile)}")

                // create zips
                String packageLocation = "$outDir/${artifactName}"
                ["zip", "-rj", "$packageLocation", "${artifactFolder.path}"].execute()

                long buildNumber = System.currentTimeMillis()
            }
        }

        String cmd = "jfrog rt u " +
                "${outDir}/*.zip " +
                "$repoKey/ " +
                "--server-id=art " +
                "--threads=15"
        println cmd
//        passed &= HelperTools.executeCommandAndPrint(cmd) == 0 ? true : false
        File uploadedFilesList = new File("uploadedFiles")
        sleep(500)
        outDir.eachFile {
            uploadedFilesList << "${it.name}\n"
        }

//        FileUtils.deleteDirectory(new File(artifactsDir))
//        FileUtils.deleteDirectory(outDir)

        return passed
    }

    /**
     * Reads the output of the generate method and verifies that the data exists
     * @param filePath The output of the generator method (stored in a file)
     * @return True if the verification succeeded
     */
    boolean verify(def filePath) {
        def toVerify = []
        // Create a map of artifact path and sha1s that we will use to verify
        (new File(filePath)).eachLine { String line ->
            if (line.startsWith("$OUTPUT_PREFIX $ADD_PREFIX ")) {
                toVerify << ["name": line.split()[2], "sha1": line.split()[3]]
            }
        }

        GParsPool.withPool NUM_OF_WORKERS, {
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
                        if (artSha1 != it.sha1) {
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
        ['jfrog', 'rt', 'c', "--url=${artifactoryUrl}", "--user=${artifactoryUser}", "--password=${artifactoryPassword}", 'art'].execute().waitForOrKill(15000)
        def toDelete = []
        // Deletes the files created by this tool
        (new File(filePath)).eachLine { String line ->
            if (line.startsWith("$OUTPUT_PREFIX $ADD_PREFIX ")) {
                toDelete << line.split()[2]
            }
        }
        // Delete in batches
        GParsPool.withPool NUM_OF_WORKERS, {
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
        if (artifactoryUrl.endsWith('/'))
            artifactoryUrl = artifactoryUrl.substring(0, artifactoryUrl.length() - 2)
        artifactoryUser = userInput.getUserInput("artifactory.user")
        artifactoryPassword = userInput.getUserInput("artifactory.password")
        repoKey = userInput.getUserInput("artifactory.repo")
        packagesAmount = userInput.getUserInput("package.number") as Integer
        packageNumberStart = userInput.getUserInput("package.number.start") as Integer
        maxFileSize = userInput.getUserInput("max.file.size") as Integer
        minFileSize = userInput.getUserInput("min.file.size") as Integer
    }

    /**
     * Generates composer.json content
     * @param name - package name
     * @param version - package version
     * @return content for composer.json as String
     */
    private static String generateComposerJson(String name) {
        """{
    "name": "$name",
    "description": "Simple package test",
    "type": "library",
    "keywords": [
        "template"
    ],
    "license": "GPL",
    "authors": [
        {
            "name": "FirstName SecondName",
            "email": "example@example.exm"
        }
    ],
    "config": {
        "platform": {
            "php": ">=7.0.0"
        },
        "optimize-autoloader": true,
        "sort-packages": true
    },
    "require": {
        "php": "^7.0"
    },
    "require-dev": {
        "phpunit/phpunit": "^8.0"
    },
    "autoload": {
        "classmap": [
            "src/"
        ]
    }
}"""
    }

}
