/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
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

import brooklyn.entity.basic.ApplicationBuilder;

import com.google.common.collect.Lists;

/**
 * Command line interface test support.
 */
public class BaseCliIntegrationTest {

    // TODO does this need to be hard-coded?
    private static final String BROOKLYN_BIN_PATH = "./target/brooklyn-dist/bin/brooklyn";
    private static final String BROOKLYN_CLASSPATH = "./target/test-classes/:./target/classes/";

    // Times in seconds to allow Brooklyn to run and produce output
    private static final long DELAY = 10l;
    private static final long TIMEOUT = DELAY + 30l;

    private ExecutorService executor;

    @BeforeMethod(alwaysRun = true)
    public void setup() {
        executor = Executors.newCachedThreadPool();
    }

    @AfterMethod(alwaysRun = true)
    public void teardown() {
        executor.shutdownNow();
    }

    /** Invoke the brooklyn script with arguments. */
    public Process startBrooklyn(String...argv) throws Throwable {
        ProcessBuilder pb = new ProcessBuilder();
        pb.environment().remove("BROOKLYN_HOME");
        pb.environment().put("BROOKLYN_CLASSPATH", BROOKLYN_CLASSPATH);
        pb.command(Lists.asList(BROOKLYN_BIN_PATH, argv));
        return pb.start();
    }

    public void testBrooklyn(Process brooklyn, BrooklynCliTest test, int expectedExit) throws Throwable {
        testBrooklyn(brooklyn, test, expectedExit, false);
    }

    /** Tests the operation of the Brooklyn CLI. */
    public void testBrooklyn(Process brooklyn, BrooklynCliTest test, int expectedExit, boolean stop) throws Throwable {
        try {
            Future<Integer> future = executor.submit(test);

            // Send CR to stop if required
            if (stop) {
                OutputStream out = brooklyn.getOutputStream();
                out.write('\n');
                out.flush();
            }

            int exitStatus = future.get(TIMEOUT, TimeUnit.SECONDS);

            // Check error code from process
            assertEquals(exitStatus, expectedExit, "Command returned wrong status");
        } catch (TimeoutException te) {
            fail("Timed out waiting for process to complete", te);
        } catch (ExecutionException ee) {
            if (ee.getCause() instanceof AssertionError) {
                throw ee.getCause();
            } else throw ee;
        } finally {
            brooklyn.destroy();
        }
    }

    /** A {@link Callable} that encapsulates Brooklyn CLI test logic. */
    public static abstract class BrooklynCliTest implements Callable<Integer> {

        private final Process brooklyn;

        private String consoleOutput;
        private String consoleError;

        public BrooklynCliTest(Process brooklyn) {
            this.brooklyn = brooklyn;
        }

        @Override
        public Integer call() throws Exception {
            // Wait for initial output
            Thread.sleep(TimeUnit.SECONDS.toMillis(DELAY));

            // Get the console output of running that command
            consoleOutput = convertStreamToString(brooklyn.getInputStream());
            consoleError = convertStreamToString(brooklyn.getErrorStream());

            // Check if the output looks as expected
            checkConsole();

            // Return exit status on completion
            return brooklyn.waitFor();
        }

        /** Perform test assertions on console output and error streams. */
        public abstract void checkConsole();

        private String convertStreamToString(InputStream is) {
            try {
                return new Scanner(is).useDelimiter("\\A").next();
            } catch (NoSuchElementException e) {
                return "";
            }
        }

        protected void assertConsoleOutput(String...expected) {
            for (String e : expected) {
                assertTrue(consoleOutput.contains(e), "Execution output not logged; output=" + consoleOutput);
            }
        }

        protected void assertNoConsoleOutput(String...expected) {
            for (String e : expected) {
                assertFalse(consoleOutput.contains(e), "Execution output logged; output=" + consoleOutput);
            }
        }

        protected void assertConsoleError(String...expected) {
            for (String e : expected) {
                assertTrue(consoleError.contains(e), "Execution error not logged; error=" + consoleError);
            }
        }

        protected void assertNoConsoleError(String...expected) {
            for (String e : expected) {
                assertFalse(consoleError.contains(e), "Execution error logged; error=" + consoleError);
            }
        }

        protected void assertConsoleOutputEmpty() {
            assertTrue(consoleOutput.isEmpty(), "Output present; output=" + consoleOutput);
        }

        protected void assertConsoleErrorEmpty() {
            assertTrue(consoleError.isEmpty(), "Error present; error=" + consoleError);
        }
    };

    /** An empty application for testing. */
    public static class TestApplication extends ApplicationBuilder {
        @Override
        protected void doBuild() {
            // Empty, for testing
        }
    }

}
