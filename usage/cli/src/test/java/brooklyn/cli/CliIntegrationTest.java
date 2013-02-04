package brooklyn.cli;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.io.InputStream;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Test the command line interface operation.
 */
public class CliIntegrationTest {

    // FIXME this should not be hardcoded; needed to use the local code for Main
    private static final String BROOKLYN_BIN_PATH = "../dist/target/brooklyn-dist/bin/brooklyn";
    private static final String BROOKLYN_CLASSPATH = "./target/test-classes/:./target/classes/";

    private ExecutorService executor;

    @BeforeMethod(alwaysRun = true)
    public void setup() {
        executor = Executors.newCachedThreadPool();
    }

    @AfterMethod(alwaysRun = true)
    public void teardown() {
        executor.shutdownNow();
    }

    // Helper function used in testing
    private String convertStreamToString(InputStream is) {
        try {
            return new Scanner(is).useDelimiter("\\A").next();
        } catch (NoSuchElementException e) {
            return "";
        }
    }

    /**
     * Checks if running {@code brooklyn help} produces the expected output.
     */
    @Test(groups = "Integration")
    public void testLaunchCliHelp() throws Throwable {
        // Invoke the brooklyn script with the "help" argument
        ProcessBuilder pb = new ProcessBuilder();
        pb.environment().remove("BROOKLYN_HOME");
        pb.environment().put("BROOKLYN_CLASSPATH", BROOKLYN_CLASSPATH);
        pb.command(BROOKLYN_BIN_PATH, "help");
        final Process brooklyn = pb.start();
 
        Callable<Void> cli = new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                // Get the console output of running that command
                String consoleOutput = convertStreamToString(brooklyn.getInputStream());
                String consoleError = convertStreamToString(brooklyn.getErrorStream());

                // Check if the output looks as expected for the help command
                assertTrue(consoleOutput.contains("usage: brooklyn"), "Usage info not present");
                assertTrue(consoleOutput.contains("The most commonly used brooklyn commands are:"), "List of common commands not present");
                assertTrue(consoleOutput.contains("help     Display help for available commands")
                        && consoleOutput.contains("info     Display information about brooklyn")
                        && consoleOutput.contains("launch   Starts a brooklyn application"), "List of common commands present");
                assertTrue(consoleOutput.contains("See 'brooklyn help <command>' for more information on a specific command."), "Implemented commands not listed");
                assertTrue(consoleError.isEmpty());

                return null;
            }
        };

        try {
            Future<Void> future = executor.submit(cli);
            future.get(10, TimeUnit.SECONDS);

            // Check error code from process is 0
            assertEquals(brooklyn.exitValue(), 0, "Command terminated with error status");
        } catch (TimeoutException te) {
            fail("Timed out waiting for process to complete");
        } catch (ExecutionException ee) {
            throw ee.getCause();
        } finally {
            brooklyn.destroy();
        }
    }

    /**
     * Checks if launching an application using {@code brooklyn launch} produces the expected output.
     */
    @Test(groups = "Integration")
    public void testLaunchCliApp() throws Throwable {
        // Invoke the brooklyn script with the launch command
        ProcessBuilder pb = new ProcessBuilder();
        pb.environment().remove("BROOKLYN_HOME");
        pb.environment().put("BROOKLYN_CLASSPATH", BROOKLYN_CLASSPATH);
        pb.command(BROOKLYN_BIN_PATH, "--verbose", "launch", "--app", "brooklyn.cli.CliTest$ExampleApp", "--location", "localhost", "--noConsole");
        final Process brooklyn = pb.start();
 
        Callable<Void> cli = new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                // Get the console output of running that command
                String consoleOutput = convertStreamToString(brooklyn.getInputStream());
                String consoleError = convertStreamToString(brooklyn.getErrorStream());

                // Check if the output looks as expected for the launch command
                assertTrue(consoleOutput.contains("Launching Brooklyn web console management"), "Launch message not output");
                assertFalse(consoleOutput.contains("Initiating Jersey application"), "Web console started");
                assertTrue(consoleOutput.contains("Started application ExampleApp"), "ExampleApp not started");
                assertTrue(consoleOutput.contains("Launched Brooklyn; now blocking to wait for cntrl-c or kill"), "Blocking message not output");
                assertTrue(consoleError.isEmpty());

                return null;
            }
        };

        try {
            Future<Void> future = executor.submit(cli);

            // Wait 15s for console output, then kill
            Thread.sleep(15000L);
            brooklyn.destroy();

            future.get(10, TimeUnit.SECONDS);
        } catch (TimeoutException te) {
            fail("Timed out waiting for process to complete");
        } catch (ExecutionException ee) {
            throw ee.getCause();
        } finally {
            brooklyn.destroy();
        }
    }

    /**
     * Checks if a correct error and help message is given if using incorrect params.
     */
    @Test(groups = "Integration")
    public void testLaunchCliAppParamError() throws Throwable {
        // Invoke the brooklyn script with incorrect arguments
        ProcessBuilder pb = new ProcessBuilder();
        pb.environment().remove("BROOKLYN_HOME");
        pb.environment().put("BROOKLYN_CLASSPATH", BROOKLYN_CLASSPATH);
        pb.command(BROOKLYN_BIN_PATH, "launch", "nothing", "--app");
        final Process brooklyn = pb.start();

        Callable<Void> cli = new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                // Get the console output of running that command
                String consoleOutput = convertStreamToString(brooklyn.getInputStream());
                String consoleError = convertStreamToString(brooklyn.getErrorStream());

                // Check if the output looks as expected
                assertTrue(consoleError.contains("Parse error: Required values for option 'application class or file' not provided"), "Parse error not reported");
                assertTrue(consoleError.contains("NAME")
                        && consoleError.contains("SYNOPSIS")
                        && consoleError.contains("OPTIONS")
                        && consoleError.contains("COMMANDS"), "Usage info not printed");
                assertTrue(consoleOutput.isEmpty());

                return null;
            }
        };

        try {
            Future<Void> future = executor.submit(cli);
            future.get(10, TimeUnit.SECONDS);

            // Check error code from process
            assertEquals(brooklyn.exitValue(), 1, "Command returned wrong status");
        } catch (TimeoutException te) {
            fail("Timed out waiting for process to complete");
        } catch (ExecutionException ee) {
            throw ee.getCause();
        } finally {
            brooklyn.destroy();
        }
    }

    /**
     * Checks if a correct error and help message is given if using incorrect command.
     */
    @Test(groups = "Integration")
    public void testLaunchCliAppCommandError() throws Throwable {
        // Invoke the brooklyn script with incorrect arguments
        ProcessBuilder pb = new ProcessBuilder();
        pb.environment().remove("BROOKLYN_HOME");
        pb.environment().put("BROOKLYN_CLASSPATH", BROOKLYN_CLASSPATH);
        pb.command(BROOKLYN_BIN_PATH, "biscuit");
        final Process brooklyn = pb.start();

        Callable<Void> cli = new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                // Get the console output of running that command
                String consoleOutput = convertStreamToString(brooklyn.getInputStream());
                String consoleError = convertStreamToString(brooklyn.getErrorStream());

                // Check if the output looks as expected
                assertTrue(consoleError.contains("Parse error: No command specified"), "Parse error not reported");
                assertTrue(consoleError.contains("NAME")
                        && consoleError.contains("SYNOPSIS")
                        && consoleError.contains("OPTIONS")
                        && consoleError.contains("COMMANDS"), "Usage info not printed");
                assertTrue(consoleOutput.isEmpty());

                return null;
            }
        };

        try {
            Future<Void> future = executor.submit(cli);
            future.get(10, TimeUnit.SECONDS);

            // Check error code from process
            assertEquals(brooklyn.exitValue(), 1, "Command returned wrong status");
        } catch (TimeoutException te) {
            fail("Timed out waiting for process to complete");
        } catch (ExecutionException ee) {
            throw ee.getCause();
        } finally {
            brooklyn.destroy();
        }
    }

    /**
     * Checks if a correct error and help message is given if using incorrect command.
     */
    @Test(groups = "Integration")
    public void testLaunchCliAppLaunchError() throws Throwable {
        // Invoke the brooklyn script with incorrect arguments
        ProcessBuilder pb = new ProcessBuilder();
        pb.environment().remove("BROOKLYN_HOME");
        pb.environment().put("BROOKLYN_CLASSPATH", BROOKLYN_CLASSPATH);
        pb.command(BROOKLYN_BIN_PATH, "launch", "--app", "org.eample.DoesNotExist", "--location", "nowhere");
        final Process brooklyn = pb.start();

        Callable<Void> cli = new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                // Get the console output of running that command
                String consoleOutput = convertStreamToString(brooklyn.getInputStream());
                String consoleError = convertStreamToString(brooklyn.getErrorStream());

                // Check if the output looks as expected
                assertTrue(consoleOutput.contains("ERROR Execution error: brooklyn.util.ResourceUtils.getResourceFromUrl"), "Execution error not logged");
                assertTrue(consoleError.contains("Execution error: Error getting resource for LaunchCommand"), "Execution error not reported");

                return null;
            }
        };

        try {
            Future<Void> future = executor.submit(cli);
            future.get(10, TimeUnit.SECONDS);

            // Check error code from process
            assertEquals(brooklyn.exitValue(), 2, "Command returned wrong status");
        } catch (TimeoutException te) {
            fail("Timed out waiting for process to complete");
        } catch (ExecutionException ee) {
            throw ee.getCause();
        } finally {
            brooklyn.destroy();
        }
    }
}
