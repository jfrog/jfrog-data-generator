#!/usr/local/bin/groovy
import groovyjarjarcommonscli.MissingArgumentException
import java.security.*
import java.io.*
import java.security.SecureRandom

class HelperTools {
/**
 * Process user input
 */
    @Singleton
    static class UserInputProcessor {
        public static final String CONFIG_PROPERTIES_FILE = "/config.properties"
        public static final String DEFAULT_CONFIG_PROPERTIES_FILE = "/config.properties.defaults"
        Properties properties = new Properties()
        Properties defaultProperties = new Properties()

        /**
         * Create a help message based on the default config properties
         */
        static def printHelp() {
            // Load the properties files
            Properties helpProps = new Properties()
            File defaultPropertiesFile = new File(DEFAULT_CONFIG_PROPERTIES_FILE)
            int propNum = 1
            String line
            String description = ''
            println "# | Property Name | Environment Variable Name | Description | Required | Default Value"
            println "--- | --- | --- | --- |--- |--- |---"
            // Load the properties
            defaultPropertiesFile.withInputStream {
                helpProps.load(it)
            }
            // Load the comments
            defaultPropertiesFile.withReader { reader ->
                while ((line = reader.readLine())!=null) {
                    if (line.startsWith('#')) {
                        if (description.size() > 0)
                            description += '. '
                        description += line.substring(1, line.length()).stripIndent()
                    } else {
                        def propName = line.substring(0, line.indexOf('='))
                        boolean required = !(helpProps.getProperty(propName))
                        println "${propNum++} | ${propName} | ${propNameToEnvName(propName)} | ${description} | " +
                                "${required} | ${helpProps.getProperty(propName)}"
                        description = ''
                    }
                }
            }
        }

        /**
         * Load user input from the provided properties or environment variable (env variable takes precedence)
         * @param propName The property name
         * @return The value either from a property, environment variable or defaults
         */
        def getUserInput(String propName) {
            init()
            String envName = propNameToEnvName(propName)
            if (System.getenv(envName)) {
                return System.getenv(envName)
            } else if (properties.getProperty(propName)) {
                return properties.getProperty(propName)
            } else if (defaultProperties.getProperty(propName)) {
                return defaultProperties.getProperty(propName)
            }
            throw new MissingArgumentException("Required property $propName (or environment variable $envName) not provided")
        }
        /**
         * Converts the property name into the environment variable equivalent
         * @param propName The property name
         * @return The environment variable name
         */
        private static String propNameToEnvName(String propName) {
            return propName.replace('.', '_').toUpperCase()
        }

        private synchronized init() {
            if (defaultProperties.size() != 0)
                return
            // Load the properties files
            File propertiesFile = new File(CONFIG_PROPERTIES_FILE)
            File defaultPropertiesFile = new File(DEFAULT_CONFIG_PROPERTIES_FILE)
            try {
                defaultPropertiesFile.withInputStream {
                    defaultProperties.load(it)
                }
                propertiesFile.withInputStream {
                    properties.load(it)
                }
            } catch (java.lang.FileNotFoundException ex) {
                // It is okay since they may be providing everything through env properties
            }
        }
    }

    /**
     * Execute a command and print the results
     * @param cmd The command to execute
     * @return The command exit value
     */
    static def executeCommandAndPrint(String cmd) {
        ProcessBuilder builder = new ProcessBuilder(cmd.split(' '))
        builder.redirectErrorStream(true)
        Process process = builder.start()

        InputStream stdout = process.getInputStream()
        BufferedReader reader = new BufferedReader(new InputStreamReader(stdout))
        String line
        while ((line = reader.readLine()) != null) {
            System.out.println("Stdout: " + line)
        }

        process.waitFor()
        return process.exitValue()
    }

    /**
     * Created a file of a specific size filled with random content
     * @param filePath The full path of the file
     * @param fileSize The size (in bytes)
     */
    static def createBinFile(File filePath, def fileSize) {
        // Handling creation of bin file as block size chunk of 32MB.
        def multipleCount = (fileSize / 33554431).intValue()
        def remainderCount = (fileSize % 33554431).intValue()
        if (multipleCount > 0) {
            def systemCallMultiple = 'dd if=/dev/urandom of=' + filePath.absoluteFile + ' bs=33554431' + " count=$multipleCount"
            def proc = systemCallMultiple.execute()
            proc.waitForOrKill(36000000)
            if (proc.exitValue()) {
                println "Error creating " + filePath.absoluteFile + ", error: " + proc.exitValue()
                throw new IOException()
            }
        }
        def systemCallRemainder = 'dd if=/dev/urandom ' + "bs=$remainderCount count=1"
        ProcessBuilder builder = new ProcessBuilder(systemCallRemainder.split(' '))
        builder.redirectErrorStream(false)
        builder.redirectOutput(ProcessBuilder.Redirect.appendTo(filePath))
        Process process = builder.start()
        process.waitFor()
    }

    /**
     *
     * @param alphabet - set of chars which will be used to generate name
     * @param n - length of generated string
     * @return String
     */
    static def generateString(String alphabet = (('A'..'Z')+('a'..'z')+('0'..'9')).join(), int n) {
        new SecureRandom().with {
            (1..n).collect { alphabet[ nextInt( alphabet.length() ) ] }.join()
        }
    }

    /**
     * Calculates the SHA1 of a file
     * @param file The file
     * @return The SHA1 string
     */
    static def getFileSha1(file) {
        def digest = MessageDigest.getInstance("SHA1")
        def inputstream = file.newInputStream()
        def buffer = new byte[16384]
        def len

        while((len=inputstream.read(buffer)) > 0) {
            digest.update(buffer, 0, len)
        }
        def sha1sum = digest.digest()

        def result = ""
        for(byte b : sha1sum) {
            result += toHex(b)
        }
        return result
    }


    private static hexChr(int b) {
        return Integer.toHexString(b & 0xF)
    }

    private static toHex(int b) {
        return hexChr((b & 0xF0) >> 4) + hexChr(b & 0x0F)
    }
}


