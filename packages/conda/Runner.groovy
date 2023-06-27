#!/usr/local/bin/groovy
import groovy.transform.WithWriteLock
//@GrabResolver(name='restlet.org', root='http://maven.restlet.org')
@Grab('org.codehaus.gpars:gpars:1.2.1')
@Grab('org.codehaus.groovy.modules.http-builder:http-builder')
@Grab('commons-io:commons-io:2.11.0')
import groovyx.gpars.GParsPool
import groovyx.net.http.RESTClient
import org.apache.commons.io.FileUtils

import java.security.SecureRandom

// Run the generator
new GenerateChef().init(args)

class GenerateChef extends Generator {
    public static final String OUTPUT_PREFIX = "##CONDA##"
    public static final String ADD_PREFIX = "ADD"
    public static final int NUM_OF_WORKERS = 8
    // User input
    def artifactoryUrl, artifactoryUser, artifactoryPassword, repoKey, packagesAmount, packageNumberStart, maxFileSize, minFileSize
    synchronized def passed = true

    /**
     * Generates chef packages and deploys them to Artifactory
     */
    @WithWriteLock
    boolean generate() {
        SecureRandom random = new SecureRandom()
        File outDir = new File("generated")
        outDir.mkdirs()
        println """What we are going to do?
We are going to build  $packagesAmount  package(s). Package size between $minFileSize and $maxFileSize bytes"""

        ['jfrog', 'rt', 'c', "--interactive=false", "--url=${artifactoryUrl}", "--user=${artifactoryUser}", "--password=${artifactoryPassword}", 'art'].execute()

        (packageNumberStart..packagesAmount).each { version ->
            String artifactName = HelperTools.generateString(9)
            File packageDir = new File(artifactName)
            packageDir.mkdirs()
            // make output dir
            File packageOutDir = new File(outDir, artifactName)
            packageOutDir.mkdirs()
            sleep(1)
            int fileSize = (minFileSize == maxFileSize) ? minFileSize : Math.abs(random.nextLong() % (maxFileSize - minFileSize)) + minFileSize
            String extraFileName = "extra_file.bin"
            File addFile = new File(packageDir, extraFileName)
            HelperTools.createBinFile(addFile, fileSize)
            println("$OUTPUT_PREFIX $ADD_PREFIX $repoKey/${packageDir}/${extraFileName} - ${fileSize / 1000} Kb")

            // meta.yaml
            String metadataName = "meta.yaml"
            File metadataFile = new File(packageDir, metadataName)
            metadataFile.withWriter { w ->
                w << generateMeta(version as String, artifactName as String)
            }
            println("$OUTPUT_PREFIX $ADD_PREFIX $repoKey/${artifactName}/${metadataName} ${HelperTools.getFileSha1(metadataFile)}")

            // build conda package
            ["conda", "build", "--output-folder", "${packageOutDir.absolutePath}", "${packageDir}"].execute().waitFor()
            packageDir.deleteDir()
        }

        String cmd = "jfrog rt u " +
                "${outDir}/*.tar.bz2 " +
                "$repoKey/ " +
                "--server-id=art " +
                "--threads=15"
        println cmd
//         save file names to separate file
//        String findPackages = 'find generated -name "*.tar.bz2" -exec basename {} +'
        HelperTools.executeCommandAndPrint(findPackages, 'files-list.csv')
        passed &= HelperTools.executeCommandAndPrint(cmd) == 0
        FileUtils.deleteDirectory(outDir)
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
                passed &= HelperTools.executeCommandAndPrint(cmd) == 0
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

    @WithWriteLock
    private String generateMeta(String version, String name) {
        """{% set name = "$name" %}
{% set version = "$version" %}

package:
  name: "{{ name|lower }}"
  version: "{{ version }}"

source:
  url: https://pypi.io/packages/source/c/click/click-7.1.2.tar.gz

build:
  noarch: generic
  number: 0
  script: "{{ PYTHON }} -m pip install . --no-deps --ignore-installed -vv "

requirements:
  host:
    - pip
    - python
  run:
    - python

about:
  license: BSD
  license_family: BSD
  summary: Composable command line interface toolkit
"""
    }

}
