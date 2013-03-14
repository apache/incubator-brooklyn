package brooklyn.cli;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.io.InputStream;
import java.io.OutputStream;
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

import brooklyn.entity.Application;
import brooklyn.entity.basic.AbstractApplication;

/**
 * Test the command line interface operation.
 */
public class CliIntegrationTest {

    // FIXME this should not be hardcoded; needed to use the local code for Main
    private static final String BROOKLYN_BIN_PATH = "./target/brooklyn-dist/bin/brooklyn";
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
 
        Callable<Integer> cli = new Callable<Integer>() {
            @Override
            public Integer call() throws Exception {
                // Get the console output of running that command
                String consoleOutput = convertStreamToString(brooklyn.getInputStream());
                String consoleError = convertStreamToString(brooklyn.getErrorStream());

                // Check if the output looks as expected for the help command
                assertTrue(consoleOutput.contains("usage: brooklyn"), "Usage info not present; output=" + consoleOutput);
                assertTrue(consoleOutput.contains("The most commonly used brooklyn commands are:"), "List of common commands not present; output=" + consoleOutput);
                assertTrue(consoleOutput.contains("help     Display help for available commands")
                        && consoleOutput.contains("info     Display information about brooklyn")
                        && consoleOutput.contains("launch   Starts a brooklyn application"), "List of common commands present; output=" + consoleOutput);
                assertTrue(consoleOutput.contains("See 'brooklyn help <command>' for more information on a specific command."),
                        "Implemented commands not listed; output=" + consoleOutput);
                assertTrue(consoleError.isEmpty(), "Output present; error=" + consoleError);

                // Return exit status on completion
                return brooklyn.waitFor();
            }
        };

        try {
            Future<Integer> future = executor.submit(cli);
            int exitStatus = future.get(10, TimeUnit.SECONDS);

            // Check error code from process is 0
            assertEquals(exitStatus, 0, "Command terminated with error status");
        } catch (TimeoutException te) {
            fail("Timed out waiting for process to complete");
        } catch (ExecutionException ee) {
            if (ee.getCause() instanceof AssertionError) {
                throw ee.getCause();
            } else throw ee;
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
        pb.command(BROOKLYN_BIN_PATH, "--verbose", "launch", "--stopOnKeyPress", "--app", "brooklyn.cli.CliIntegrationTest$TestApplication", "--location", "localhost", "--noConsole");
        final Process brooklyn = pb.start();
 
        Callable<Integer> cli = new Callable<Integer>() {
            @Override
            public Integer call() throws Exception {
                // Get the console output of running that command
                Thread.sleep(5000L);
                String consoleOutput = convertStreamToString(brooklyn.getInputStream());
                String consoleError = convertStreamToString(brooklyn.getErrorStream());

                // Check if the output looks as expected for the launch command
                assertTrue(consoleOutput.contains("Launching Brooklyn web console management"), "Launch message not output; output=" + consoleOutput);
                assertFalse(consoleOutput.contains("Initiating Jersey application"), "Web console started; output=" + consoleOutput);
                assertTrue(consoleOutput.contains("Started application TestApplication"), "Application not started; output=" + consoleOutput);
                assertTrue(consoleOutput.contains("Server started. Press return to stop."), "Server started message not output; output=" + consoleOutput);
                assertTrue(consoleError.isEmpty(), "Output present; error=" + consoleError);

                // Return exit status on completion
                return brooklyn.waitFor();
            }
        };

        try {
            Future<Integer> future = executor.submit(cli);

            // Send CR to stop
            OutputStream out = brooklyn.getOutputStream();
            out.write('\n');
            out.flush();

            int exitStatus = future.get(30, TimeUnit.SECONDS);

            // Check error code from process is 0
            assertEquals(exitStatus, 0, "Command terminated with error status");
        } catch (TimeoutException te) {
            fail("Timed out waiting for process to complete");
        } catch (ExecutionException ee) {
            if (ee.getCause() instanceof AssertionError) {
                throw ee.getCause();
            } else throw ee;
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

        Callable<Integer> cli = new Callable<Integer>() {
            @Override
            public Integer call() throws Exception {
                // Get the console output of running that command
                String consoleOutput = convertStreamToString(brooklyn.getInputStream());
                String consoleError = convertStreamToString(brooklyn.getErrorStream());

                // Check if the output looks as expected
                assertTrue(consoleError.contains("Parse error: Required values for option 'application class or file' not provided"), "Parse error not reported; error=" + consoleError);
                assertTrue(consoleError.contains("NAME")
                        && consoleError.contains("SYNOPSIS")
                        && consoleError.contains("OPTIONS")
                        && consoleError.contains("COMMANDS"), "Usage info not printed; error=" + consoleError);
                assertTrue(consoleOutput.isEmpty(), "Output present; output=" + consoleOutput);

                // Return exit status on completion
                return brooklyn.waitFor();
            }
        };

        try {
            Future<Integer> future = executor.submit(cli);
            int exitStatus = future.get(10, TimeUnit.SECONDS);

            // Check error code from process
            assertEquals(exitStatus, 1, "Command returned wrong status");
        } catch (TimeoutException te) {
            fail("Timed out waiting for process to complete");
        } catch (ExecutionException ee) {
            if (ee.getCause() instanceof AssertionError) {
                throw ee.getCause();
            } else throw ee;
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

        Callable<Integer> cli = new Callable<Integer>() {
            @Override
            public Integer call() throws Exception {
                // Get the console output of running that command
                String consoleOutput = convertStreamToString(brooklyn.getInputStream());
                String consoleError = convertStreamToString(brooklyn.getErrorStream());

                // Check if the output looks as expected
                assertTrue(consoleError.contains("Parse error: No command specified"), "Parse error not reported; error=" + consoleError);
                assertTrue(consoleError.contains("NAME")
                        && consoleError.contains("SYNOPSIS")
                        && consoleError.contains("OPTIONS")
                        && consoleError.contains("COMMANDS"), "Usage info not printed; error=" + consoleError);
                assertTrue(consoleOutput.isEmpty(), "Output present; output=" + consoleOutput);

                // Return exit status on completion
                return brooklyn.waitFor();
            }
        };

        try {
            Future<Integer> future = executor.submit(cli);
            int exitStatus = future.get(10, TimeUnit.SECONDS);

            // Check error code from process
            assertEquals(exitStatus, 1, "Command returned wrong status");
        } catch (TimeoutException te) {
            fail("Timed out waiting for process to complete");
        } catch (ExecutionException ee) {
            if (ee.getCause() instanceof AssertionError) {
                throw ee.getCause();
            } else throw ee;
        } finally {
            brooklyn.destroy();
        }
    }

    /**
     * Checks if a correct error and help message is given if using incorrect application.
     */
    @Test(groups = "Integration")
    public void testLaunchCliAppLaunchError() throws Throwable {
        // Invoke the brooklyn script with incorrect arguments
        ProcessBuilder pb = new ProcessBuilder();
        pb.environment().remove("BROOKLYN_HOME");
        pb.environment().put("BROOKLYN_CLASSPATH", BROOKLYN_CLASSPATH);
        pb.command(BROOKLYN_BIN_PATH, "launch", "--app", "org.eample.DoesNotExist", "--location", "nowhere");
        final Process brooklyn = pb.start();

        Callable<Integer> cli = new Callable<Integer>() {
            @Override
            public Integer call() throws Exception {
                // Get the console output of running that command
                String consoleOutput = convertStreamToString(brooklyn.getInputStream());
                String consoleError = convertStreamToString(brooklyn.getErrorStream());

                // Check if the output looks as expected
                assertTrue(consoleOutput.contains("ERROR Execution error: brooklyn.util.ResourceUtils.getResourceFromUrl"), "Execution error not logged; output=" + consoleOutput);
                assertTrue(consoleError.contains("Execution error: Error getting resource for LaunchCommand"), "Execution error not reported; error=" + consoleError);

                // Return exit status on completion
                return brooklyn.waitFor();
            }
        };

        try {
            Future<Integer> future = executor.submit(cli);
            int exitStatus = future.get(10, TimeUnit.SECONDS);

            // Check error code from process
            assertEquals(exitStatus, 2, "Command returned wrong status");
        } catch (TimeoutException te) {
            fail("Timed out waiting for process to complete");
        } catch (ExecutionException ee) {
            if (ee.getCause() instanceof AssertionError) {
                throw ee.getCause();
            } else throw ee;
        } finally {
            brooklyn.destroy();
        }
    }

    /** An empty {@link Application} for testing. */
    @SuppressWarnings("serial")
    public static class TestApplication extends AbstractApplication {
        public TestApplication() {
            // Empty, for testing
        }
    }

}
