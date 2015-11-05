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
package org.apache.brooklyn.cli;

import org.testng.annotations.Test;

/**
 * Test the command line interface operation.
 */
public class CliIntegrationTest extends BaseCliIntegrationTest {

    /**
     * Checks if running {@code brooklyn help} produces the expected output.
     */
    @Test(groups = {"Integration","Broken"})
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

    /*
        Exception java.io.IOException
        
        Message: Cannot run program "./target/brooklyn-dist/bin/brooklyn": error=2, No such file or directory
        Stacktrace:
        
        
        at java.lang.ProcessBuilder.start(ProcessBuilder.java:1047)
        at org.apache.brooklyn.cli.BaseCliIntegrationTest.startBrooklyn(BaseCliIntegrationTest.java:75)
        at org.apache.brooklyn.cli.CliIntegrationTest.testLaunchCliApp(CliIntegrationTest.java:56)
        at sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
        at sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:57)
        at sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
        at java.lang.reflect.Method.invoke(Method.java:606)
        at org.testng.internal.MethodInvocationHelper.invokeMethod(MethodInvocationHelper.java:84)
        at org.testng.internal.Invoker.invokeMethod(Invoker.java:714)
        at org.testng.internal.Invoker.invokeTestMethod(Invoker.java:901)
        at org.testng.internal.Invoker.invokeTestMethods(Invoker.java:1231)
        at org.testng.internal.TestMethodWorker.invokeTestMethods(TestMethodWorker.java:127)
        at org.testng.internal.TestMethodWorker.run(TestMethodWorker.java:111)
        at org.testng.TestRunner.privateRun(TestRunner.java:767)
        at org.testng.TestRunner.run(TestRunner.java:617)
        at org.testng.SuiteRunner.runTest(SuiteRunner.java:348)
        at org.testng.SuiteRunner.runSequentially(SuiteRunner.java:343)
        at org.testng.SuiteRunner.privateRun(SuiteRunner.java:305)
        at org.testng.SuiteRunner.run(SuiteRunner.java:254)
        at org.testng.SuiteRunnerWorker.runSuite(SuiteRunnerWorker.java:52)
        at org.testng.SuiteRunnerWorker.run(SuiteRunnerWorker.java:86)
        at org.testng.TestNG.runSuitesSequentially(TestNG.java:1224)
        at org.testng.TestNG.runSuitesLocally(TestNG.java:1149)
        at org.testng.TestNG.run(TestNG.java:1057)
        at org.apache.maven.surefire.testng.TestNGExecutor.run(TestNGExecutor.java:115)
        at org.apache.maven.surefire.testng.TestNGDirectoryTestSuite.executeMulti(TestNGDirectoryTestSuite.java:205)
        at org.apache.maven.surefire.testng.TestNGDirectoryTestSuite.execute(TestNGDirectoryTestSuite.java:108)
        at org.apache.maven.surefire.testng.TestNGProvider.invoke(TestNGProvider.java:111)
        at org.apache.maven.surefire.booter.ForkedBooter.invokeProviderInSameClassLoader(ForkedBooter.java:203)
        at org.apache.maven.surefire.booter.ForkedBooter.runSuitesInProcess(ForkedBooter.java:155)
        at org.apache.maven.surefire.booter.ForkedBooter.main(ForkedBooter.java:103)
        Caused by: java.io.IOException: error=2, No such file or directory
        at java.lang.UNIXProcess.forkAndExec(Native Method)
        at java.lang.UNIXProcess.<init>(UNIXProcess.java:186)
        at java.lang.ProcessImpl.start(ProcessImpl.java:130)
        at java.lang.ProcessBuilder.start(ProcessBuilder.java:1028)
        ... 30 more
     */
    /**
     * Checks if launching an application using {@code brooklyn launch} produces the expected output.
     */
    @Test(groups = {"Integration","Broken"})
    public void testLaunchCliApp() throws Throwable {
        final Process brooklyn = startBrooklyn("--verbose", "launch", "--stopOnKeyPress", "--app", "org.apache.brooklyn.cli.BaseCliIntegrationTest$TestApplication", "--location", "localhost", "--noConsole");

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
    @Test(groups = {"Integration","Broken"})
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

    /*
        Exception java.io.IOException
        
        Message: Cannot run program "./target/brooklyn-dist/bin/brooklyn": error=2, No such file or directory
        Stacktrace:
        
        
        at java.lang.ProcessBuilder.start(ProcessBuilder.java:1047)
        at org.apache.brooklyn.cli.BaseCliIntegrationTest.startBrooklyn(BaseCliIntegrationTest.java:75)
        at org.apache.brooklyn.cli.CliIntegrationTest.testLaunchCliAppCommandError(CliIntegrationTest.java:96)
        at sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
        at sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java:57)
        at sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java:43)
        at java.lang.reflect.Method.invoke(Method.java:606)
        at org.testng.internal.MethodInvocationHelper.invokeMethod(MethodInvocationHelper.java:84)
        at org.testng.internal.Invoker.invokeMethod(Invoker.java:714)
        at org.testng.internal.Invoker.invokeTestMethod(Invoker.java:901)
        at org.testng.internal.Invoker.invokeTestMethods(Invoker.java:1231)
        at org.testng.internal.TestMethodWorker.invokeTestMethods(TestMethodWorker.java:127)
        at org.testng.internal.TestMethodWorker.run(TestMethodWorker.java:111)
        at org.testng.TestRunner.privateRun(TestRunner.java:767)
        at org.testng.TestRunner.run(TestRunner.java:617)
        at org.testng.SuiteRunner.runTest(SuiteRunner.java:348)
        at org.testng.SuiteRunner.runSequentially(SuiteRunner.java:343)
        at org.testng.SuiteRunner.privateRun(SuiteRunner.java:305)
        at org.testng.SuiteRunner.run(SuiteRunner.java:254)
        at org.testng.SuiteRunnerWorker.runSuite(SuiteRunnerWorker.java:52)
        at org.testng.SuiteRunnerWorker.run(SuiteRunnerWorker.java:86)
        at org.testng.TestNG.runSuitesSequentially(TestNG.java:1224)
        at org.testng.TestNG.runSuitesLocally(TestNG.java:1149)
        at org.testng.TestNG.run(TestNG.java:1057)
        at org.apache.maven.surefire.testng.TestNGExecutor.run(TestNGExecutor.java:115)
        at org.apache.maven.surefire.testng.TestNGDirectoryTestSuite.executeMulti(TestNGDirectoryTestSuite.java:205)
        at org.apache.maven.surefire.testng.TestNGDirectoryTestSuite.execute(TestNGDirectoryTestSuite.java:108)
        at org.apache.maven.surefire.testng.TestNGProvider.invoke(TestNGProvider.java:111)
        at org.apache.maven.surefire.booter.ForkedBooter.invokeProviderInSameClassLoader(ForkedBooter.java:203)
        at org.apache.maven.surefire.booter.ForkedBooter.runSuitesInProcess(ForkedBooter.java:155)
        at org.apache.maven.surefire.booter.ForkedBooter.main(ForkedBooter.java:103)
        Caused by: java.io.IOException: error=2, No such file or directory
        at java.lang.UNIXProcess.forkAndExec(Native Method)
        at java.lang.UNIXProcess.<init>(UNIXProcess.java:186)
        at java.lang.ProcessImpl.start(ProcessImpl.java:130)
        at java.lang.ProcessBuilder.start(ProcessBuilder.java:1028)
        ... 30 more
     */
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
    @Test(groups = {"Integration","Broken"})
    public void testLaunchCliAppLaunchError() throws Throwable {
        final String app = "org.eample.DoesNotExist";
        final Process brooklyn = startBrooklyn("launch", "--app", app, "--location", "nowhere");

        BrooklynCliTest test = new BrooklynCliTest(brooklyn) {
            @Override
            public void checkConsole() {
                assertConsoleOutput(app, "not found");
                assertConsoleError("ERROR", "Fatal", "getting resource", app);
            }
        };

        testBrooklyn(brooklyn, test, 2);
    }

}
