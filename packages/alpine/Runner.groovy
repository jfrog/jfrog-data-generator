#!/usr/local/bin/groovy
@GrabResolver(name = 'jcenter', root = 'https://jcenter.bintray.com/')
@Grab('org.codehaus.gpars:gpars:0.9')
@Grab('org.codehaus.groovy.modules.http-builder:http-builder:0.7.2')
@Grab('commons-io:commons-io:1.2')
import groovyx.gpars.GParsPool
import org.apache.commons.io.FileUtils
import java.security.SecureRandom
import groovyx.net.http.RESTClient

import java.security.Timestamp
import java.time.ZonedDateTime

// Run the generator
new GeneratePHP().init(args)

class GeneratePHP extends Generator {
    public static final String OUTPUT_PREFIX = "##ALPINE##"
    public static final String ADD_PREFIX = "ADD"
    public static final int NUM_OF_WORKERS = 8
    // User input
    def artifactoryUrl, artifactoryUser, artifactoryPassword, repoKey, packagesAmount, packageNumberStart, maxFileSize, minFileSize
    synchronized def passed = true
    SecureRandom random = new SecureRandom()
    String artifactsDir = "artifacts"
    File outDir = new File("generated")

    /**
     * Generates alpine packages and deploys them to Artifactory
     */
    boolean generate() {
        outDir.mkdirs()
        println """ What we are going to do?
        We are going to build $packagesAmount packages"""

        ['jfrog', 'rt', 'c', 'art', "--url=${artifactoryUrl}", "--user=${artifactoryUser}", "--password=${artifactoryPassword}"].execute().waitForOrKill(15000)

        GParsPool.withPool 15, {
            (packageNumberStart..packagesAmount).eachParallel {
                String artifactName = HelperTools.generateString(9)
                println "Creating artifacts ${artifactName}"
                String baseFolder = "$artifactsDir/$artifactName"
                File artifactFolder = new File("$baseFolder/usr/bin")
                artifactFolder.mkdirs()
                sleep(1)
                int fileSize = (minFileSize == maxFileSize) ? minFileSize : Math.abs(random.nextLong() % (maxFileSize - minFileSize)) + minFileSize
                File addFile = new File(artifactFolder, artifactName)
                HelperTools.createBinFile(addFile, fileSize)
                println("$OUTPUT_PREFIX $ADD_PREFIX $repoKey/${artifactFolder}/${artifactName} ${HelperTools.getFileSha1(addFile)}")

                // .PKGINFO
                String fileName = ".PKGINFO"
                File pkgInfoFile = new File(baseFolder, fileName)
                String minVersion = random.nextInt(99)
                String version = "${it}.${minVersion}"
                pkgInfoFile << generatePkgInfo(artifactName as String, System.currentTimeMillis() as String, version, fileSize as String)
                println("$OUTPUT_PREFIX $ADD_PREFIX $repoKey/${artifactFolder}/${fileName} ${HelperTools.getFileSha1(pkgInfoFile)}")
            }
        }
        generateOnePackage("packToDelete")
        // pack artifacts
        "bash pack.sh".execute()

        String cmd = "jfrog rt u " +
                "${outDir}/*.apk " +
                "$repoKey/ " +
                "--server-id=art " +
                "--threads=15"
        println cmd
        passed &= HelperTools.executeCommandAndPrint(cmd) == 0 ? true : false
        File uploadedFilesList = new File("uploadedFiles")
        sleep(200)
        outDir.eachFile {
            uploadedFilesList << "${it.name}\n"
        }

        FileUtils.deleteDirectory(new File(artifactsDir))
        FileUtils.deleteDirectory(outDir)

        return passed
    }

    def generateOnePackage(String packageName) {
        outDir.mkdirs()
        println """ What we are going to do?
        We are going to build $packageName package"""

        String baseFolder = "$artifactsDir/$packageName"
        File artifactFolder = new File("$baseFolder/usr/bin")
        artifactFolder.mkdirs()
        sleep(1)
        int fileSize = (minFileSize == maxFileSize) ? minFileSize : Math.abs(random.nextLong() % (maxFileSize - minFileSize)) + minFileSize
        File addFile = new File(artifactFolder, packageName)
        HelperTools.createBinFile(addFile, fileSize)
        println("$OUTPUT_PREFIX $ADD_PREFIX $repoKey/${artifactFolder}/${packageName} ${HelperTools.getFileSha1(addFile)}")

        // .PKGINFO
        String fileName = ".PKGINFO"
        File pkgInfoFile = new File(baseFolder, fileName)
        String minVersion = random.nextInt(99)
        String majVersion = random.nextInt(50)
        String version = "${majVersion}.${minVersion}"
        pkgInfoFile << generatePkgInfo(packageName as String, System.currentTimeMillis() as String, version, fileSize as String)
        println("$OUTPUT_PREFIX $ADD_PREFIX $repoKey/${artifactFolder}/${fileName} ${HelperTools.getFileSha1(pkgInfoFile)}")
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
    private static String generatePkgInfo(String packageName, String date, String version, String size) {
        """# Generated by abuild 3.3.0_pre1-r3
# using fakeroot version 1.23
# Thu Dec 20 07:57:11 UTC 2018
pkgname = ${packageName}
pkgver = ${version}
pkgdesc = Check shell scripts for POXIX compliance
url = http://example.com
builddate = ${date}
packager = Buildozer <alpine-devel@lists.alpinelinux.org>
size = ${size}
arch = noarch
origin = ${packageName}
commit = Aq2bb5zWb8h3PgiD583Rmp95aHqQkL
maintainer = Simon Else <simon@mailinator.com>
license = GPL-2.0-or-later
depend = perl
# automatically detected:
provides = cmd:${packageName}
datahash = 75eb060ff702e074dfd44a73530f1784088d7331e783bdd8223e5fcb432349df
"""
    }

}
