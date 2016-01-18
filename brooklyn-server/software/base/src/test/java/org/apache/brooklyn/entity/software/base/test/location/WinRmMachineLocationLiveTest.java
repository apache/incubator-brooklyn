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
package org.apache.brooklyn.entity.software.base.test.location;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotEquals;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.brooklyn.api.location.MachineProvisioningLocation;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.internal.BrooklynProperties;
import org.apache.brooklyn.core.mgmt.internal.ManagementContextInternal;
import org.apache.brooklyn.core.test.entity.LocalManagementContextForTests;
import org.apache.brooklyn.core.test.entity.TestApplication;
import org.apache.brooklyn.location.winrm.WinRmMachineLocation;
import org.apache.brooklyn.util.core.internal.winrm.WinRmToolResponse;
import org.apache.brooklyn.util.text.Identifiers;
import org.apache.brooklyn.util.time.Time;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

/**
 * Tests execution of commands (batch and powershell) on Windows over WinRM, and of
 * file upload.
 * 
 * There are limitations with what is supported by winrm4j. These are highlighted in
 * tests marked as "WIP" (see individual tests).
 * 
 * These limitations are documented in docs/guide/yaml/winrm/index.md.
 * Please update the docs if you encountered new situations, or change the behaviuor 
 * of existing use-cases.
 */

public class WinRmMachineLocationLiveTest {
    private static final int MAX_EXECUTOR_THREADS = 100;

    /*
     * TODO: Deferred implementing copyFrom or environment variables.
     */
    
    private static final Logger LOG = LoggerFactory.getLogger(WinRmMachineLocationLiveTest.class);

    private static final String INVALID_CMD = "thisCommandDoesNotExistAEFafiee3d";
    private static final String PS_ERR_ACTION_PREF_EQ_STOP = "$ErrorActionPreference = \"Stop\"";

    protected MachineProvisioningLocation<WinRmMachineLocation> loc;
    protected TestApplication app;
    protected ManagementContextInternal mgmt;

    protected WinRmMachineLocation machine;
    
    private ListeningExecutorService executor;
    
    @BeforeClass(alwaysRun=true)
    public void setUpClass() throws Exception {
        executor = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(MAX_EXECUTOR_THREADS));
        
        mgmt = new LocalManagementContextForTests(BrooklynProperties.Factory.newDefault());
        
        loc = newLoc(mgmt);
        machine = loc.obtain(ImmutableMap.of());
        
        LOG.info("PROVISIONED: "+machine.getAddress()+":"+machine.config().get(WinRmMachineLocation.WINRM_PORT)
                +", "+machine.getUser()+":"+machine.config().get(WinRmMachineLocation.PASSWORD));
    }
    
    @AfterClass(alwaysRun=true)
    public void tearDownClass() throws Exception {
        try {
            if (executor != null) executor.shutdownNow();
            if (machine != null) loc.release(machine);
            if (mgmt != null) Entities.destroyAll(mgmt);
        } catch (Throwable t) {
            LOG.error("Caught exception in tearDown method", t);
        } finally {
            executor = null;
            mgmt = null;
        }
    }

    /**
     * Returns a location for obtaining a single WinRM machine. This method will be called once during 
     * {@code @BeforeClass}, then {@code loc.obtain()} will be called. The obtained machine will be
     * released in {@code @AfterClass}. 
     */
    protected MachineProvisioningLocation<WinRmMachineLocation> newLoc(ManagementContextInternal mgmt) throws Exception {
        return WindowsTestFixture.setUpWindowsLocation(mgmt);
    }
    
    @Test(groups="Live")
    public void testCopyTo() throws Exception {
        String contents = "abcdef";
        runCopyTo(contents);
        runCopyFileTo(contents);
    }
    
    // Takes several minutes to upload/download!
    @Test(groups="Live")
    public void testLargeFileCopyTo() throws Exception {
        String contents = Identifiers.makeRandomId(65537);
        runCopyTo(contents);
        runCopyFileTo(contents);
    }
    
    protected void runCopyTo(String contents) throws Exception {
        String remotePath = "C:\\myfile-"+Identifiers.makeRandomId(4)+".txt";
        machine.copyTo(new ByteArrayInputStream(contents.getBytes()), remotePath);
        
        WinRmToolResponse response = machine.executeCommand("type "+remotePath);
        String msg = "statusCode="+response.getStatusCode()+"; out="+response.getStdOut()+"; err="+response.getStdErr();
        assertEquals(response.getStatusCode(), 0, msg);
        assertEquals(response.getStdOut().trim(), contents, msg);
    }
    
    protected void runCopyFileTo(String contents) throws Exception {
        File localFile = File.createTempFile("winrmtest"+Identifiers.makeRandomId(4), ".txt");
        try {
            Files.write(contents, localFile, Charsets.UTF_8);
            String remotePath = "C:\\myfile-"+Identifiers.makeRandomId(4)+".txt";
            machine.copyTo(localFile, remotePath);
            
            WinRmToolResponse response = machine.executeCommand("type "+remotePath);
            String msg = "statusCode="+response.getStatusCode()+"; out="+response.getStdOut()+"; err="+response.getStdErr();
            assertEquals(response.getStatusCode(), 0, msg);
            assertEquals(response.getStdOut().trim(), contents, msg);
        } finally {
            localFile.delete();
        }
    }
    
    @Test(groups="Live")
    public void testExecScript() throws Exception {
        assertExecSucceeds("echo myline", "myline", "");
    }
    
    /*
     * TODO Not supported in winrm4j (or PyWinRM).
     * 
     * Just gives "first", and exit code 1.
     */
    @Test(groups={"Live", "WIP"}, enabled=false)
    public void testExecMultiLineScript() throws Exception {
        assertExecSucceeds("echo first" + "\r\n" + "echo second", "first"+"\r\n"+"second", "");
    }
    
    @Test(groups={"Live"})
    public void testExecMultiPartScript() throws Exception {
        assertExecSucceeds(ImmutableList.of("echo first", "echo second"), "first "+"\r\n"+"second", "");
    }
    
    @Test(groups="Live")
    public void testExecFailingScript() throws Exception {
        final String INVALID_CMD = "thisCommandDoesNotExistAEFafiee3d";
        
        // Single commands
        assertExecFails(INVALID_CMD);
        assertExecFails(ImmutableList.of(INVALID_CMD));
    }

    @Test(groups="Live")
    public void testExecScriptExit0() throws Exception {
        assertExecSucceeds("exit /B 0", "", "");
        assertExecSucceeds(ImmutableList.of("exit /B 0"), "", "");
    }

    /*
     * TODO Not supported in winrm4j (or PyWinRM).
     * 
     * Executing (in python):
     *     import winrm
     *     s = winrm.Session('52.12.211.247', auth=('Administrator', 'pa55w0rd'))
     *     r = s.run_cmd("exit /B 1")
     * gives exit code 0.
     */
    @Test(groups={"Live", "WIP"})
    public void testExecScriptExit1() throws Exception {
        // Single commands
        assertExecFails("exit /B 1");
        assertExecFails(ImmutableList.of("exit /B 1"));
    }

    @Test(groups="Live")
    public void testExecBatchFileSingleLine() throws Exception {
        String script = "EXIT /B 0";
        String scriptPath = "C:\\myscript-"+Identifiers.makeRandomId(4)+".bat";
        machine.copyTo(new ByteArrayInputStream(script.getBytes()), scriptPath);

        assertExecSucceeds(scriptPath, null, "");
    }

    @Test(groups="Live")
    public void testExecBatchFileMultiLine() throws Exception {
        String script = Joiner.on("\n").join(
                "@ECHO OFF",
                "echo first", 
                "echo second", 
                "EXIT /B 0");
        String scriptPath = "C:\\myscript-"+Identifiers.makeRandomId(4)+".bat";
        machine.copyTo(new ByteArrayInputStream(script.getBytes()), scriptPath);

        assertExecSucceeds(scriptPath, "first"+"\r\n"+"second", "");
    }

    @Test(groups="Live")
    public void testExecBatchFileWithArgs() throws Exception {
        String script = Joiner.on("\n").join(
                "@ECHO OFF",
                "echo got %1", 
                "echo got %2", 
                "EXIT /B 0");
        String scriptPath = "C:\\myscript-"+Identifiers.makeRandomId(4)+".bat";
        machine.copyTo(new ByteArrayInputStream(script.getBytes()), scriptPath);

        assertExecSucceeds(scriptPath+" first second", "got first"+"\r\n"+"got second", "");
    }

    @Test(groups="Live")
    public void testExecBatchFileWithExit1() throws Exception {
        String script = "EXIT /B 1";
        String scriptPath = "C:\\myscript-"+Identifiers.makeRandomId(4)+".bat";
        machine.copyTo(new ByteArrayInputStream(script.getBytes()), scriptPath);

        assertExecFails(scriptPath);
    }

    @Test(groups="Live")
    public void testExecCorruptExe() throws Exception {
        String exe = "garbage";
        String exePath = "C:\\myscript-"+Identifiers.makeRandomId(4)+".exe";
        machine.copyTo(new ByteArrayInputStream(exe.getBytes()), exePath);

        assertExecFails(exePath);
    }

    @Test(groups="Live")
    public void testExecFilePs() throws Exception {
        String script = Joiner.on("\r\n").join(
                "Write-Host myline", 
                "exit 0");
        String scriptPath = "C:\\myscript-"+Identifiers.makeRandomId(4)+".ps1";
        machine.copyTo(new ByteArrayInputStream(script.getBytes()), scriptPath);

        assertExecPsSucceeds(
                "PowerShell -NonInteractive -NoProfile -Command "+scriptPath,
                "myline",
                "");
    }

    @Test(groups="Live")
    public void testExecFilePsWithExit1() throws Exception {
        String script = Joiner.on("\r\n").join(
                "Write-Host myline", 
                "exit 1");
        String scriptPath = "C:\\myscript-"+Identifiers.makeRandomId(4)+".ps1";
        machine.copyTo(new ByteArrayInputStream(script.getBytes()), scriptPath);

        assertExecFails("PowerShell -NonInteractive -NoProfile -Command "+scriptPath);
    }

    /*
     * TODO Not supported in PyWinRM - single line .ps1 file with "exit 1" gives an
     * exit code 0 over PyWinRM, but an exit code 1 when executed locally!
     * 
     * Executing (in python):
     *     import winrm
     *     s = winrm.Session('52.12.211.247', auth=('Administrator', 'pa55w0rd'))
     *     r = s.run_cmd("PowerShell -NonInteractive -NoProfile -Command C:\singleLineExit1.ps1")
     * gives exit code 0.
     */
    @Test(groups={"Live", "WIP"})
    public void testExecFilePsWithSingleLineExit1() throws Exception {
        String script = "exit 1";
        String scriptPath = "C:\\myscript-"+Identifiers.makeRandomId(4)+".ps1";
        machine.copyTo(new ByteArrayInputStream(script.getBytes()), scriptPath);

        assertExecFails("PowerShell -NonInteractive -NoProfile -Command "+scriptPath);
    }

    @Test(groups="Live")
    public void testExecPsScript() throws Exception {
        assertExecPsSucceeds("Write-Host myline", "myline", "");
    }
    
    @Test(groups="Live")
    public void testExecPsMultiLineScript() throws Exception {
        // Note stdout is "\n" rather than "\r\n" (the latter is returned for run_cmd, versus run_ps)
        assertExecPsSucceeds("Write-Host first" + "\r\n" + "Write-Host second", "first"+"\n"+"second", "");
    }
    
    @Test(groups="Live")
    public void testExecPsMultiLineScriptWithoutSlashR() throws Exception {
        assertExecPsSucceeds("Write-Host first" + "\n" + "Write-Host second", "first"+"\n"+"second", "");
    }
    
    @Test(groups="Live")
    public void testExecPsMultiPartScript() throws Exception {
        assertExecPsSucceeds(ImmutableList.of("Write-Host first", "Write-Host second"), "first"+"\n"+"second", "");
    }

    @Test(groups="Live")
    public void testExecPsBatchFile() throws Exception {
        String script = "EXIT /B 0";
        String scriptPath = "C:\\myscript-"+Identifiers.makeRandomId(4)+".bat";
        machine.copyTo(new ByteArrayInputStream(script.getBytes()), scriptPath);

        assertExecPsSucceeds("& '"+scriptPath+"'", null, "");
    }
    
    @Test(groups="Live")
    public void testExecPsBatchFileExit1() throws Exception {
        String script = "EXIT /B 1";
        String scriptPath = "C:\\myscript-"+Identifiers.makeRandomId(4)+".bat";
        machine.copyTo(new ByteArrayInputStream(script.getBytes()), scriptPath);

        assertExecPsFails("& '"+scriptPath+"'");
    }

    /*
     * TODO Not supported in PyWinRM - gives exit status 1, rather than the 3 from the batch file.
     */
    @Test(groups={"Live", "WIP"})
    public void testExecPsBatchFileExit3() throws Exception {
        String script = "EXIT /B 3";
        String scriptPath = "C:\\myscript-"+Identifiers.makeRandomId(4)+".bat";
        machine.copyTo(new ByteArrayInputStream(script.getBytes()), scriptPath);

        WinRmToolResponse response = machine.executePsScript("& '"+scriptPath+"'");
        String msg = "statusCode="+response.getStatusCode()+"; out="+response.getStdOut()+"; err="+response.getStdErr();
        assertEquals(response.getStatusCode(), 3, msg);
    }

    @Test(groups="Live")
    public void testExecPsCorruptExe() throws Exception {
        String exe = "garbage";
        String exePath = "C:\\myscript-"+Identifiers.makeRandomId(4)+".exe";
        machine.copyTo(new ByteArrayInputStream(exe.getBytes()), exePath);

        assertExecPsFails("& '"+exePath+"'");
    }

    @Test(groups="Live")
    public void testExecPsFileWithArg() throws Exception {
        String script = Joiner.on("\r\n").join(
                "Param(",
                "  [string]$myarg",
                ")",
                "Write-Host got $myarg", 
                "exit 0");
        String scriptPath = "C:\\myscript-"+Identifiers.makeRandomId(4)+".ps1";
        machine.copyTo(new ByteArrayInputStream(script.getBytes()), scriptPath);

        assertExecPsSucceeds("& "+scriptPath+" -myarg myval", "got myval", "");
    }

    @Test(groups="Live")
    public void testExecPsFilePs() throws Exception {
        String script = Joiner.on("\r\n").join(
                "Write-Host myline", 
                "exit 0");
        String scriptPath = "C:\\myscript-"+Identifiers.makeRandomId(4)+".ps1";
        machine.copyTo(new ByteArrayInputStream(script.getBytes()), scriptPath);

        assertExecPsSucceeds("& "+scriptPath, "myline", "");
    }

    @Test(groups="Live")
    public void testExecPsFilePsWithExit1() throws Exception {
        String script = Joiner.on("\r\n").join(
                "Write-Host myline", 
                "exit 1");
        String scriptPath = "C:\\myscript-"+Identifiers.makeRandomId(4)+".ps1";
        machine.copyTo(new ByteArrayInputStream(script.getBytes()), scriptPath);
        System.out.println(scriptPath);

        assertExecPsFails("& "+scriptPath);
    }

    /*
     * TODO Not supported in PyWinRM - single line .ps1 file with "exit 1" gives an
     * exit code 0 over PyWinRM, but an exit code 1 when executed locally!
     * 
     * Executing (in python):
     *     import winrm
     *     s = winrm.Session('52.12.211.247', auth=('Administrator', 'pa55w0rd'))
     *     r = s.run_cmd("PowerShell -NonInteractive -NoProfile -Command C:\singleLineExit1.ps1")
     * gives exit code 0.
     */
    @Test(groups={"Live", "WIP"})
    public void testExecPsFilePsSingleLineWithExit1() throws Exception {
        String script = "exit 1";
        String scriptPath = "C:\\myscript-"+Identifiers.makeRandomId(4)+".ps1";
        machine.copyTo(new ByteArrayInputStream(script.getBytes()), scriptPath);

        assertExecPsFails("& "+scriptPath);
    }

    /*
     * TODO Not supported in winrm4j - single line .ps1 file with "exit 1" gives an
     * exit code 0 over PyWinRM, but an exit code 1 when executed locally!
     * 
     * Executing (in python):
     *     import winrm
     *     s = winrm.Session('52.12.211.247', auth=('Administrator', 'pa55w0rd'))
     *     r = s.run_cmd("PowerShell -NonInteractive -NoProfile -Command C:\singleLineGarbage.ps1")
     * gives exit code 0.
     */
    @Test(groups={"Live", "WIP"})
    public void testExecPsFilePsSingleLineWithInvalidCommand() throws Exception {
        String script = INVALID_CMD;
        String scriptPath = "C:\\myscript-"+Identifiers.makeRandomId(4)+".ps1";
        machine.copyTo(new ByteArrayInputStream(script.getBytes()), scriptPath);

        assertExecPsFails("& "+scriptPath);
    }

    @Test(groups="Live")
    public void testConfirmUseOfErrorActionPreferenceDoesNotCauseErr() throws Exception {
        // Confirm that ErrorActionPreference=Stop does not itself cause a failure, and still get output on success.
        assertExecPsSucceeds(ImmutableList.of(PS_ERR_ACTION_PREF_EQ_STOP, "Write-Host myline"), "myline", "");
    }

    /*
     * TODO Not supported in PyWinRM
     * 
     * Executing (in python):
     *     import winrm
     *     s = winrm.Session('52.12.211.247', auth=('Administrator', 'pa55w0rd'))
     *     r = s.run_ps("exit 1")
     * gives exit code 0.
     */
    @Test(groups={"Live", "WIP"})
    public void testExecPsExit1() throws Exception {
        // Single commands
        assertExecPsFails("exit 1");
        assertExecPsFails(ImmutableList.of("exit 1"));
        
        // Multi-part
        assertExecPsFails(ImmutableList.of(PS_ERR_ACTION_PREF_EQ_STOP, "Write-Host myline", "exit 1"));
        
        // Multi-line
        assertExecPsFails(PS_ERR_ACTION_PREF_EQ_STOP + "\n" + "Write-Host myline" + "\n" + "exit 1");
    }

    @Test(groups="Live")
    public void testExecFailingPsScript() throws Exception {
        // Single commands
        assertExecPsFails(INVALID_CMD);
        assertExecPsFails(ImmutableList.of(INVALID_CMD));
        
        // Multi-part commands
        assertExecPsFails(ImmutableList.of(PS_ERR_ACTION_PREF_EQ_STOP, "Write-Host myline", INVALID_CMD));
        assertExecPsFails(ImmutableList.of(PS_ERR_ACTION_PREF_EQ_STOP, INVALID_CMD, "Write-Host myline"));
    }

    @Test(groups={"Live", "Acceptance"})
    public void testExecConcurrently() throws Exception {
        final int NUM_RUNS = 10;
        final int TIMEOUT_MINS = 30;
        final AtomicInteger counter = new AtomicInteger();
        
        // Find the test methods that are enabled, and that are not WIP 
        List<Method> methodsToRun = Lists.newArrayList();
        Method[] allmethods = WinRmMachineLocationLiveTest.class.getMethods();
        for (Method method : allmethods) {
            Test annotatn = method.getAnnotation(Test.class);
            if (method.getParameterTypes().length != 0) {
                continue;
            }
            if (method.getName().equals("testExecConcurrently")) {
                continue;
            }
            if (annotatn == null || !annotatn.enabled()) {
                continue;
            }
            String[] groups = annotatn.groups();
            if (groups != null && Arrays.asList(groups).contains("WIP")) {
                continue;
            }
            methodsToRun.add(method);
        }

        // Execute all the methods many times
        LOG.info("Executing "+methodsToRun.size()+" methods "+NUM_RUNS+" times each, with "+MAX_EXECUTOR_THREADS+" threads for concurrent execution; max permitted time "+TIMEOUT_MINS+"mins; methods="+methodsToRun);
        
        List<ListenableFuture<?>> results = Lists.newArrayList();
        for (int i = 0; i < NUM_RUNS; i++) {
            for (final Method method : methodsToRun) {
                results.add(executor.submit(new Callable<Void>() {
                    public Void call() throws Exception {
                        LOG.info("Executing "+method.getName()+" in thread "+Thread.currentThread());
                        Stopwatch stopwatch = Stopwatch.createStarted();
                        try {
                            method.invoke(WinRmMachineLocationLiveTest.this);
                            LOG.info("Executed "+method.getName()+" in "+Time.makeTimeStringRounded(stopwatch)+", in thread "+Thread.currentThread()+"; total "+counter.incrementAndGet()+" methods done");
                            return null;
                        } catch (Exception e) {
                            LOG.error("Execute failed for "+method.getName()+" after "+Time.makeTimeStringRounded(stopwatch)+", in thread "+Thread.currentThread()+"; total "+counter.incrementAndGet()+" methods done");
                            throw e;
                        }
                    }}));
            }
        }
        
        Futures.allAsList(results).get(TIMEOUT_MINS, TimeUnit.MINUTES);
    }

    private void assertExecFails(String cmd) {
        Stopwatch stopwatch = Stopwatch.createStarted();
        assertFailed(cmd, machine.executeCommand(cmd), stopwatch);
    }

    private void assertExecFails(List<String> cmds) {
        Stopwatch stopwatch = Stopwatch.createStarted();
        assertFailed(cmds, machine.executeCommand(cmds), stopwatch);
    }
    
    private void assertExecPsFails(String cmd) {
        Stopwatch stopwatch = Stopwatch.createStarted();
        assertFailed(cmd, machine.executePsScript(cmd), stopwatch);
    }
    
    private void assertExecPsFails(List<String> cmds) {
        Stopwatch stopwatch = Stopwatch.createStarted();
        assertFailed(cmds, machine.executePsScript(cmds), stopwatch);
    }

    private void assertExecSucceeds(String cmd, String stdout, String stderr) {
        Stopwatch stopwatch = Stopwatch.createStarted();
        assertSucceeded(cmd, machine.executeCommand(cmd), stdout, stderr, stopwatch);
    }

    private void assertExecSucceeds(List<String> cmds, String stdout, String stderr) {
        Stopwatch stopwatch = Stopwatch.createStarted();
        assertSucceeded(cmds, machine.executeCommand(cmds), stdout, stderr, stopwatch);
    }

    private void assertExecPsSucceeds(String cmd, String stdout, String stderr) {
        Stopwatch stopwatch = Stopwatch.createStarted();
        assertSucceeded(cmd, machine.executePsScript(cmd), stdout, stderr, stopwatch);
    }

    private void assertExecPsSucceeds(List<String> cmds, String stdout, String stderr) {
        Stopwatch stopwatch = Stopwatch.createStarted();
        assertSucceeded(cmds, machine.executePsScript(cmds), stdout, stderr, stopwatch);
    }

    private void assertFailed(Object cmd, WinRmToolResponse response, Stopwatch stopwatch) {
        String msg = "statusCode="+response.getStatusCode()+"; out="+response.getStdOut()+"; err="+response.getStdErr();
        LOG.info("Executed in "+Time.makeTimeStringRounded(stopwatch)+" (asserting failed): "+msg+"; cmd="+cmd);
        assertNotEquals(response.getStatusCode(), 0, msg);
    }
    
    private WinRmToolResponse assertSucceeded(Object cmd, WinRmToolResponse response, String stdout, String stderr, Stopwatch stopwatch) {
        String msg = "statusCode="+response.getStatusCode()+"; out="+response.getStdOut()+"; err="+response.getStdErr();
        LOG.info("Executed in "+Time.makeTimeStringRounded(stopwatch)+" (asserting success): "+msg+"; cmd="+cmd);
        assertEquals(response.getStatusCode(), 0, msg);
        if (stdout != null) assertEquals(response.getStdOut().trim(), stdout, msg);
        if (stderr != null) assertEquals(response.getStdErr().trim(), stderr, msg);
        return response;
    }
}
