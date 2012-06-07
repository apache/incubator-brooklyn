package brooklyn.cli;

import static org.testng.Assert.assertTrue;
import java.io.InputStream;
import java.util.NoSuchElementException;
import java.util.Scanner;

import org.testng.annotations.Test;

import brooklyn.entity.basic.AbstractApplication;

// TODO finish up the tests and enable them
public class CliIntegrationTest {
    
    // FIXME: this should not be hardcoded
    String brooklynBinPath = "../dist/target/brooklyn-0.4.0-M1-dist/brooklyn/bin/";
    
    // Helper function used in testing
    private String convertStreamToString(InputStream is) {
        try {
            return new Scanner(is).useDelimiter("\\A").next();
        } catch (NoSuchElementException e) {
            return "";
        }
    }
    
    // An empty app to be used for testing
    @SuppressWarnings("serial")
    public static class ExampleApp extends AbstractApplication { }
    
    /**
     * Checks if running "brooklyn help" produces the expected output.
     * @throws Exception
     */
    @Test(enabled = true, groups = "Integration")
    public void testLaunchCliHelp() throws Exception {
        // Invoke the brooklyn script with the "help" argument
        ProcessBuilder pb = new ProcessBuilder();
        pb.command(brooklynBinPath+"brooklyn", "help");;
        Process brooklyn = pb.start();
        // Get the console output of running that command
        String consoleOutput = convertStreamToString(brooklyn.getInputStream());
        // Check if the output looks as expected for the help command
        boolean usagePresent = consoleOutput.contains("usage: brooklyn");
        assertTrue(usagePresent, "Usage info present");
        boolean commonCommands = consoleOutput.contains("The most commonly used brooklyn commands are:");
        assertTrue(commonCommands, "List of common commands present");
        boolean specificCommandHelp = consoleOutput.contains("See 'brooklyn help <command>' for more information on a specific command.");
        assertTrue(specificCommandHelp, "Show how to get help for specific commands");
        // Check error code from process is 0
        assertTrue(brooklyn.exitValue()==0,"Command terminates succesfully");
    }

    /**
     * TODO: Finish up this test
     * Checks if launching a brooklyn app produces the expected output.
     * @throws Exception
     */
    @Test(enabled = false, groups = "Integration")
    public void testLaunchCliApp() throws Exception {
        // Invoke the brooklyn script with the launch command
        ProcessBuilder pb = new ProcessBuilder();
        pb.command(brooklynBinPath+"brooklyn", "--verbose", "launch", "--app", "brooklyn.cli.CliTest.ExampleApp", "--location", "localhost", "--noConsole");
        Process brooklyn = pb.start();
        // Get the console output of running that command
        String consoleOutput = convertStreamToString(brooklyn.getInputStream());
        //TODO: need to kill brooklyn after the app gets started, perhaps set a timeout . . .
    }

    /**
     * Checks if a correct error + help message is given if using incorrect params.
     * @throws Exception
     */
    @Test(enabled = true, groups = "Integration")
    public void testLaunchCliAppError() throws Exception {
        // Invoke the brooklyn script with incorrect arguments
        ProcessBuilder pb = new ProcessBuilder();
        pb.command(brooklynBinPath+"brooklyn", "launch", "--doesNotExist", "nothing");
        Process brooklyn = pb.start();
        // Get the console output of running that command
        String consoleOutput = convertStreamToString(brooklyn.getErrorStream());
        // Check if the output looks as expected
        boolean parseError = consoleOutput.contains("Parse error:");
        assertTrue(parseError, "Parse error detected");
        boolean showUsage = consoleOutput.contains("NAME")
                && consoleOutput.contains("SYNOPSIS")
                && consoleOutput.contains("OPTIONS")
                && consoleOutput.contains("COMMANDS");
        assertTrue(showUsage, "Show usage info");
        // Check error code from process is 0
        assertTrue(brooklyn.exitValue()!=0,"Command terminates with error");
    }
    
}
