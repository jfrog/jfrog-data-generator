#!/usr/local/bin/groovy
@GrabResolver(name = 'jcenter', root = 'https://jcenter.bintray.com/')
@Grab('org.codehaus.gpars:gpars:0.9')
@Grab('org.codehaus.groovy.modules.http-builder:http-builder:0.7.2')
@Grab('commons-io:commons-io:1.2')
import groovyx.gpars.GParsPool
import org.apache.commons.io.FileUtils
import groovyx.net.http.RESTClient

// Run the generator
new GenerateMaven().init(args)


class GenerateMaven extends Generator {
    public static final String OUTPUT_PREFIX="##MAVEN##"
    public static final String ADD_PREFIX="ADD"
    public static final int NUM_OF_WORKERS = 8
    // User input
    def artifactoryUrl, artifactoryUser, artifactoryPassword, repoKey, groupId, artifactIds, majorStart, major,
            minorStart, minor, packageType, numOfDeployments, noOfFiles, fileSize
    def passed = true

    /**
     * Generates maven packages and deploys them Artifactory
     */
    boolean generate() {
        println """ What we are going to do?
        We are going to build  $artifactIds  packages, each with  $major  major versions, each one of those major version will have
          $minor  minor version. We are going to deploy  $packageType  type of packages.
        Number of deployments into each version folder will be:  $numOfDeployments .
        Each version will have 4 maven files: jar, source jar, javadoc jar, pom. """

        if ( noOfFiles > 0 ) {
            println "Each version will have additional $noOfFiles files, each file size is $fileSize"
        }


        def ext = ""

        ['jfrog', 'rt', 'c', "--url=${artifactoryUrl}", "--user=${artifactoryUser}", "--password=${artifactoryPassword}", 'art'].execute ().waitForOrKill (15000)

        GParsPool.withPool 12, {
            for (int artifactId = 1; artifactId < artifactIds.toInteger() + 1; artifactId++) {
                String filePath
                String artifactName = (packageType.contains("plugin") ? "plugin" : "multi")
                if (packageType.contains("snapshot")) {
                    println "Creating snapshot versions for multi${artifactId}"
                    ext = "-SNAPSHOT"
                    filePath = "${groupId.replace(".", "/")}/" + artifactName + artifactId
                } else {
                    println "Creating release versions for multi${artifactId}"
                    filePath = "${groupId.replace(".", "/")}/" + artifactName + artifactId
                }

                File srcJar = new File("jars/multi20.jar")
                File srcTestsJar = new File("jars/multi20-tests.jar")
                File srcSourcesJar = new File("jars/multi20-sources.jar")

                def jarSha1 = HelperTools.getFileSha1(srcJar)
                def testsJarSha1 = HelperTools.getFileSha1(srcTestsJar)
                def sourcesJarSha1 = HelperTools.getFileSha1(srcSourcesJar)

                (majorStart..(majorStart + major - 1).toInteger()).eachParallel { maj ->
                    (minorStart..(minorStart + minor - 1).toInteger()).each { min ->
                        def version = "$maj.$min$ext"
                        File versionFolder = new File("$filePath/$version")
                        versionFolder.mkdirs()
                        noOfFiles.times { fileNo ->
                            String extraFileName =  artifactName + artifactId + "-$fileNo-${version}.bin"
                            File addFile = new File(versionFolder, extraFileName)
                            HelperTools.createBinFile(addFile, fileSize)
                            println("$OUTPUT_PREFIX $ADD_PREFIX $repoKey/${versionFolder}/${extraFileName} ${HelperTools.getFileSha1(addFile)}")
                        }
                        // Version pom
                        String versionPomFilename = artifactName + artifactId + "-${version}.pom"
                        File versionPom = new File(versionFolder, versionPomFilename)
                        versionPom << generatePom(version, artifactId, groupId, packageType)
                        println("$OUTPUT_PREFIX $ADD_PREFIX $repoKey/${versionFolder}/${versionPomFilename} ${HelperTools.getFileSha1(versionPom)}")

                        // Jar
                        String destJarFilename =  artifactName + artifactId + "-${version}.jar"
                        File destJar = new File(versionFolder, destJarFilename)
                        FileUtils.copyFile(srcJar, destJar)
                        println("$OUTPUT_PREFIX $ADD_PREFIX $repoKey/${versionFolder}/${destJarFilename} ${jarSha1}")

                        // Test jar
                        String destTestsJarFilename = artifactName + artifactId + "-${version}-tests.jar"
                        File destTestsJar = new File(versionFolder, destTestsJarFilename)
                        FileUtils.copyFile(srcTestsJar, destTestsJar)
                        println("$OUTPUT_PREFIX $ADD_PREFIX $repoKey/${versionFolder}/${destTestsJarFilename} ${testsJarSha1}")

                        // Sources jar
                        String destSourcesJarFilename = artifactName + artifactId + "-${version}-sources.jar"
                        File destSourcesJar = new File(versionFolder, destSourcesJarFilename)
                        FileUtils.copyFile(srcSourcesJar, destSourcesJar)
                        println("$OUTPUT_PREFIX $ADD_PREFIX $repoKey/${versionFolder}/${destSourcesJarFilename} ${sourcesJarSha1}")
                    }
                }

                long buildNumber = System.currentTimeMillis()
                String cmd = "jfrog rt upload " +
                        "--server-id=art " +
                        "--flat=false --threads=${packageType.contains("snapshot") ? 1 : 15} " +
                        "--build-name=dummy-project --build-number=${buildNumber} " +
                        "${filePath}/ " +
                        "$repoKey/"
                println cmd
                passed &= HelperTools.executeCommandAndPrint(cmd) == 0 ? true : false
                FileUtils.deleteDirectory(new File(filePath))
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
        if ( artifactoryUrl.endsWith ( '/' ) )
            artifactoryUrl = artifactoryUrl.substring (0, artifactoryUrl.length ( ) - 2 )
        artifactoryUser = userInput.getUserInput("artifactory.user")
        artifactoryPassword = userInput.getUserInput("artifactory.password")
        repoKey = userInput.getUserInput("artifactory.repo")
        groupId = userInput.getUserInput("package.group.id")
        artifactIds = userInput.getUserInput("package.number") as Integer
        majorStart = userInput.getUserInput("package.major.start") as Integer
        major = userInput.getUserInput("package.major.versions") as Integer
        minorStart = userInput.getUserInput("package.major.minor.start") as Integer
        minor = userInput.getUserInput("package.major.minor.versions") as Integer
        packageType = userInput.getUserInput("package.type")
        numOfDeployments = (packageType.contains("snapshot") ? userInput.getUserInput("package.snapshots.number") : 1) as Integer
        noOfFiles = userInput.getUserInput("package.extra.files.number") as Integer
        fileSize = userInput.getUserInput("package.extra.files.size") as Integer

        // TODO: Verify input
    }
    private String generatePom(String version, int artifactId, String group, String packageType) {

        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<project xmlns=\"http://maven.apache.org/POM/4.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd\">\n" +
                "    <modelVersion>4.0.0</modelVersion>\n" +
                "    <parent>\n" +
                "        <groupId>" + group + "</groupId>\n" +
                "        <artifactId>multi</artifactId>\n" +
                "        <version>1.1.1</version>\n" +
                "    </parent>\n" +
                "\n" +
                "    <artifactId>" + (packageType.contains("plugin") ? "plugin" : "multi") + artifactId + "</artifactId>\n" +
                "    <packaging>" + (packageType.contains("plugin") ? "maven-plugin" : "jar") + "</packaging>\n" +
                "    <name>Multi 1</name>\n" +
                "    <version>" + version + "</version>\n" +
                "\n" +
                "    <licenses>\n" +
                "        <license>\n" +
                "            <name>apache</name>\n" +
                "            <comments>none</comments>\n" +
                "        </license>\n" +
                "    </licenses>\n" +
                "    <dependencies>\n" +
                "        <dependency>\n" +
                "            <groupId>org.apache.commons</groupId>\n" +
                "            <artifactId>commons-email</artifactId>\n" +
                "            <version>1.1</version>\n" +
                "            <scope>compile</scope>\n" +
                "        </dependency>\n" +
                "        <dependency>\n" +
                "            <groupId>org.codehaus.plexus</groupId>\n" +
                "            <artifactId>plexus-utils</artifactId>\n" +
                "            <version>1.5.1</version>\n" +
                "        </dependency>\n" +
                "        <dependency>\n" +
                "            <groupId>javax.servlet.jsp</groupId>\n" +
                "            <artifactId>jsp-api</artifactId>\n" +
                "            <version>2.1</version>\n" +
                "            <scope>compile</scope>\n" +
                "        </dependency>\n" +
                "        <dependency>\n" +
                "            <groupId>commons-io</groupId>\n" +
                "            <artifactId>commons-io</artifactId>\n" +
                "            <version>1.4</version>\n" +
                "        </dependency>\n" +
                "        <dependency>\n" +
                "            <groupId>org.springframework</groupId>\n" +
                "            <artifactId>spring-aop</artifactId>\n" +
                "            <version>2.5.6</version>\n" +
                "        </dependency>\n" +
                "\n" +
                "        <!-- dependency with classifier -->\n" +
                "        <dependency>\n" +
                "            <groupId>org.testng</groupId>\n" +
                "            <artifactId>testng</artifactId>\n" +
                "            <classifier>jdk15</classifier>\n" +
                "            <version>5.9</version>\n" +
                "            <scope>test</scope>\n" +
                "        </dependency>\n" +
                "    </dependencies>\n" +
                "</project>"
    }

}
