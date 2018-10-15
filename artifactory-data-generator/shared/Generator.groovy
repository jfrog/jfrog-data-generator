/**
 * Generic generator that should be implemented by all generators
 */
abstract class Generator {
    public static final String VERIFY_INPUT_FILE = "/verify.txt"
    public static final String CLEANUP_INPUT_FILE = "/cleanup.txt"
    def userInput = HelperTools.UserInputProcessor.instance
    /**
     * Called to generate and deploy the data
     * Should print enough information to stdout such that the verify/cleanup function can do its job
     * @return True if the generation succeeded
     */
    abstract boolean generate()
    /**
     * Reads the output of the generate method and verifies that the data exists
     * @param filePath The output of the generator method (stored in a file)
     * @return True if the verification succeeded
     */
    abstract boolean verify(def filePath)
    /**
     * Reads the output of the generate method and cleans up
     * @param filePath The output of the generator method (stored in a file)
     */
    abstract boolean cleanup(def filepath)
    /**
     * Collects and verified the user provided input, transforming it as needed and erroring out if needed
     */
    abstract void getInput()

    /**
     * Default process for the tool
     */
    def void init(def args) {
        // Hidden functionality useful for building/documentation
        if (args) {
            if (args[0] == "--import-grapes") {
                println "Just importing dependencies"
                System.exit(0)
            }
        }
        if (System.getenv("PRINT_HELP")) {
            userInput.printHelp()
            System.exit(0)
        }
        // If the verify file exists, verify against the provided Art
        getInput()
        if (new File(VERIFY_INPUT_FILE).exists()) {
            if (verify(VERIFY_INPUT_FILE)) {
                println("Verification succeeded!")
            } else {
                println("Verification failed!")
            }
        } else if (new File(CLEANUP_INPUT_FILE).exists()) {
            if (cleanup(CLEANUP_INPUT_FILE)) {
                println("Cleanup succeeded!")
            } else {
                println("Cleanup failed!")
            }
        } else {
            generate()
        }
    }
}