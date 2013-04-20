package brooklyn.cli;

import org.testng.annotations.Test;

/**
 * Test the command line interface operation.
 */
public class CliIntegrationTest extends BaseCliIntegrationTest {

    /**
     * Checks if running {@code brooklyn help} produces the expected output.
     */
    @Test(groups = "Integration")
    public void testLaunchCliHelp() throws Throwable {
        final Process brooklyn = startBrooklyn("help");

        BrooklynCliTest test = new BrooklynCliTest(brooklyn) {
            @Override
            public void checkConsole() {
                assertConsoleOutput("usage: brooklyn"); // Usage info not present
                assertConsoleOutput("The most commonly used brooklyn commands are:");
                assertConsoleOutput("help     Display help for available commands",
                                    "info     Display information about brooklyn",
                                    "launch   Starts a brooklyn application"); // List of common commands not present
                assertConsoleOutput("See 'brooklyn help <command>' for more information on a specific command.");
                assertConsoleErrorEmpty();
            }
        };

        testBrooklyn(brooklyn, test, 0);
    }

    /**
     * Checks if launching an application using {@code brooklyn launch} produces the expected output.
     */
    @Test(groups = "Integration")
    public void testLaunchCliApp() throws Throwable {
        final Process brooklyn = startBrooklyn("--verbose", "launch", "--stopOnKeyPress", "--app", "brooklyn.cli.BaseCliIntegrationTest$TestApplication", "--location", "localhost", "--noConsole");

        BrooklynCliTest test = new BrooklynCliTest(brooklyn) {
            @Override
            public void checkConsole() {
                assertConsoleOutput("Launching brooklyn app:"); // Launch message not output
                assertNoConsoleOutput("Initiating Jersey application"); // Web console started
                assertConsoleOutput("Started application BasicApplicationImpl"); // Application not started
                assertConsoleOutput("Server started. Press return to stop."); // Server started message not output
                assertConsoleErrorEmpty();
            }
        };

        testBrooklyn(brooklyn, test, 0, true);
    }

    /**
     * Checks if a correct error and help message is given if using incorrect param.
     */
    @Test(groups = "Integration")
    public void testLaunchCliAppParamError() throws Throwable {
        final Process brooklyn = startBrooklyn("launch", "nothing", "--app");

        BrooklynCliTest test = new BrooklynCliTest(brooklyn) {
            @Override
            public void checkConsole() {
                assertConsoleError("Parse error: Required values for option 'application class or file' not provided");
                assertConsoleError("NAME", "SYNOPSIS", "OPTIONS", "COMMANDS");
                assertConsoleOutputEmpty();
            }
        };

        testBrooklyn(brooklyn, test, 1);
    }

    /**
     * Checks if a correct error and help message is given if using incorrect command.
     */
    @Test(groups = "Integration")
    public void testLaunchCliAppCommandError() throws Throwable {
        final Process brooklyn = startBrooklyn("biscuit");

        BrooklynCliTest test = new BrooklynCliTest(brooklyn) {
            @Override
            public void checkConsole() {
                assertConsoleError("Parse error: No command specified");
                assertConsoleError("NAME", "SYNOPSIS", "OPTIONS", "COMMANDS");
                assertConsoleOutputEmpty();
            }
        };

        testBrooklyn(brooklyn, test, 1);
    }

    /**
     * Checks if a correct error and help message is given if using incorrect application.
     */
    @Test(groups = "Integration")
    public void testLaunchCliAppLaunchError() throws Throwable {
        final Process brooklyn = startBrooklyn("launch", "--app", "org.eample.DoesNotExist", "--location", "nowhere");

        BrooklynCliTest test = new BrooklynCliTest(brooklyn) {
            @Override
            public void checkConsole() {
                assertConsoleOutput("ERROR Execution error: brooklyn.util.ResourceUtils.getResourceFromUrl");
                assertConsoleError("Execution error: Error getting resource for LaunchCommand");
            }
        };

        testBrooklyn(brooklyn, test, 2);
    }

}
