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
package brooklyn.util.ssh;

import static java.lang.String.format;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.Entities;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.management.ManagementContext;
import brooklyn.test.entity.LocalManagementContextForTests;
import brooklyn.util.javalang.JavaClassNames;
import brooklyn.util.net.Networking;
import brooklyn.util.os.Os;
import brooklyn.util.task.BasicExecutionContext;
import brooklyn.util.task.ssh.SshTasks;
import brooklyn.util.task.system.ProcessTaskWrapper;
import brooklyn.util.text.Identifiers;
import brooklyn.util.text.Strings;
import brooklyn.util.time.Duration;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;

public class BashCommandsIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(BashCommandsIntegrationTest.class);
    
    private ManagementContext mgmt;
    private BasicExecutionContext exec;
    
    private File destFile;
    private File sourceNonExistantFile;
    private File sourceFile1;
    private File sourceFile2;
    private String sourceNonExistantFileUrl;
    private String sourceFileUrl1;
    private String sourceFileUrl2;
    private SshMachineLocation loc;

    private String localRepoFilename = "localrepofile.txt";
    private File localRepoBasePath;
    private File localRepoEntityBasePath;
    private String localRepoEntityVersionPath;
    private File localRepoEntityFile;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        mgmt = new LocalManagementContextForTests();
        exec = new BasicExecutionContext(mgmt.getExecutionManager());
        
        destFile = Os.newTempFile(getClass(), "commoncommands-test-dest.txt");
        
        sourceNonExistantFile = new File("/this/does/not/exist/ERQBETJJIG1234");
        sourceNonExistantFileUrl = sourceNonExistantFile.toURI().toString();
        
        sourceFile1 = Os.newTempFile(getClass(), "commoncommands-test.txt");
        sourceFileUrl1 = sourceFile1.toURI().toString();
        Files.write("mysource1".getBytes(), sourceFile1);
        
        sourceFile2 = Os.newTempFile(getClass(), "commoncommands-test2.txt");
        sourceFileUrl2 = sourceFile2.toURI().toString();
        Files.write("mysource2".getBytes(), sourceFile2);

        localRepoEntityVersionPath = JavaClassNames.simpleClassName(this)+"-test-dest-"+Identifiers.makeRandomId(8);
        localRepoBasePath = new File(format("%s/.brooklyn/repository", System.getProperty("user.home")));
        localRepoEntityBasePath = new File(localRepoBasePath, localRepoEntityVersionPath);
        localRepoEntityFile = new File(localRepoEntityBasePath, localRepoFilename);
        localRepoEntityBasePath.mkdirs();
        Files.write("mylocal1".getBytes(), localRepoEntityFile);

        loc = mgmt.getLocationManager().createLocation(LocalhostMachineProvisioningLocation.spec()).obtain();
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (sourceFile1 != null) sourceFile1.delete();
        if (sourceFile2 != null) sourceFile2.delete();
        if (destFile != null) destFile.delete();
        if (localRepoEntityFile != null) localRepoEntityFile.delete();
        if (localRepoEntityBasePath != null) FileUtils.deleteDirectory(localRepoEntityBasePath);
        if (loc != null) loc.close();
        if (mgmt != null) Entities.destroyAll(mgmt);
    }
    
    @Test(groups="Integration")
    public void testSudo() throws Exception {
        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        ByteArrayOutputStream errStream = new ByteArrayOutputStream();
        String cmd = BashCommands.sudo("whoami");
        int exitcode = loc.execCommands(ImmutableMap.of("out", outStream, "err", errStream), "test", ImmutableList.of(cmd));
        String outstr = new String(outStream.toByteArray());
        String errstr = new String(errStream.toByteArray());
        
        assertEquals(exitcode, 0, "out="+outstr+"; err="+errstr);
        assertTrue(outstr.contains("root"), "out="+outstr+"; err="+errstr);
    }
    
    public void testDownloadUrl() throws Exception {
        List<String> cmds = BashCommands.commandsToDownloadUrlsAs(
                ImmutableList.of(sourceFileUrl1), 
                destFile.getAbsolutePath());
        int exitcode = loc.execCommands("test", cmds);
        
        assertEquals(0, exitcode);
        assertEquals(Files.readLines(destFile, Charsets.UTF_8), ImmutableList.of("mysource1"));
    }
    
    @Test(groups="Integration")
    public void testDownloadFirstSuccessfulFile() throws Exception {
        List<String> cmds = BashCommands.commandsToDownloadUrlsAs(
                ImmutableList.of(sourceNonExistantFileUrl, sourceFileUrl1, sourceFileUrl2), 
                destFile.getAbsolutePath());
        int exitcode = loc.execCommands("test", cmds);
        
        assertEquals(0, exitcode);
        assertEquals(Files.readLines(destFile, Charsets.UTF_8), ImmutableList.of("mysource1"));
    }
    
    @Test(groups="Integration")
    public void testDownloadToStdout() throws Exception {
        ProcessTaskWrapper<String> t = SshTasks.newSshExecTaskFactory(loc, 
                "cd "+destFile.getParentFile().getAbsolutePath(),
                BashCommands.downloadToStdout(Arrays.asList(sourceFileUrl1))+" | sed s/my/your/")
            .requiringZeroAndReturningStdout().newTask();

        String result = exec.submit(t).get();
        assertTrue(result.trim().equals("yoursource1"), "Wrong contents of stdout download: "+result);
    }

    @Test(groups="Integration")
    public void testAlternativesWhereFirstSucceeds() throws Exception {
        ProcessTaskWrapper<Integer> t = SshTasks.newSshExecTaskFactory(loc)
                .add(BashCommands.alternatives(Arrays.asList("echo first", "exit 88")))
                .newTask();

        Integer returnCode = exec.submit(t).get();
        String stdout = t.getStdout();
        String stderr = t.getStderr();
        log.info("alternatives for good first command gave: "+returnCode+"; err="+stderr+"; out="+stdout);
        assertTrue(stdout.contains("first"), "errcode="+returnCode+"; stdout="+stdout+"; stderr="+stderr);
        assertEquals(returnCode, (Integer)0);
    }

    @Test(groups="Integration")
    public void testAlternatives() throws Exception {
        ProcessTaskWrapper<Integer> t = SshTasks.newSshExecTaskFactory(loc)
                .add(BashCommands.alternatives(Arrays.asList("asdfj_no_such_command_1", "exit 88")))
                .newTask();

        Integer returnCode = exec.submit(t).get();
        log.info("alternatives for bad commands gave: "+returnCode+"; err="+new String(t.getStderr())+"; out="+new String(t.getStdout()));
        assertEquals(returnCode, (Integer)88);
    }

    @Test(groups="Integration")
    public void testRequireTestHandlesFailure() throws Exception {
        ProcessTaskWrapper<?> t = SshTasks.newSshExecTaskFactory(loc)
            .add(BashCommands.requireTest("-f "+sourceNonExistantFile.getPath(),
                    "The requested file does not exist")).newTask();

        exec.submit(t).get();
        assertNotEquals(t.getExitCode(), (Integer)0);
        assertTrue(t.getStderr().contains("The requested file"), "Expected message in: "+t.getStderr());
        assertTrue(t.getStdout().contains("The requested file"), "Expected message in: "+t.getStdout());
    }

    @Test(groups="Integration")
    public void testRequireTestHandlesSuccess() throws Exception {
        ProcessTaskWrapper<?> t = SshTasks.newSshExecTaskFactory(loc)
            .add(BashCommands.requireTest("-f "+sourceFile1.getPath(),
                    "The requested file does not exist")).newTask();

        exec.submit(t).get();
        assertEquals(t.getExitCode(), (Integer)0);
        assertTrue(t.getStderr().equals(""), "Expected no stderr messages, but got: "+t.getStderr());
    }

    @Test(groups="Integration")
    public void testRequireFileHandlesFailure() throws Exception {
        ProcessTaskWrapper<?> t = SshTasks.newSshExecTaskFactory(loc)
            .add(BashCommands.requireFile(sourceNonExistantFile.getPath())).newTask();

        exec.submit(t).get();
        assertNotEquals(t.getExitCode(), (Integer)0);
        assertTrue(t.getStderr().contains("required file"), "Expected message in: "+t.getStderr());
        assertTrue(t.getStderr().contains(sourceNonExistantFile.getPath()), "Expected message in: "+t.getStderr());
        assertTrue(t.getStdout().contains("required file"), "Expected message in: "+t.getStdout());
        assertTrue(t.getStdout().contains(sourceNonExistantFile.getPath()), "Expected message in: "+t.getStdout());
    }

    @Test(groups="Integration")
    public void testRequireFileHandlesSuccess() throws Exception {
        ProcessTaskWrapper<?> t = SshTasks.newSshExecTaskFactory(loc)
            .add(BashCommands.requireFile(sourceFile1.getPath())).newTask();

        exec.submit(t).get();
        assertEquals(t.getExitCode(), (Integer)0);
        assertTrue(t.getStderr().equals(""), "Expected no stderr messages, but got: "+t.getStderr());
    }

    @Test(groups="Integration")
    public void testRequireFailureExitsImmediately() throws Exception {
        ProcessTaskWrapper<?> t = SshTasks.newSshExecTaskFactory(loc)
            .add(BashCommands.requireTest("-f "+sourceNonExistantFile.getPath(),
                    "The requested file does not exist"))
            .add("echo shouldnae come here").newTask();

        exec.submit(t).get();
        assertNotEquals(t.getExitCode(), (Integer)0);
        assertTrue(t.getStderr().contains("The requested file"), "Expected message in: "+t.getStderr());
        assertTrue(t.getStdout().contains("The requested file"), "Expected message in: "+t.getStdout());
        Assert.assertFalse(t.getStdout().contains("shouldnae"), "Expected message in: "+t.getStdout());
    }

    @Test(groups="Integration")
    public void testPipeMultiline() throws Exception {
        String output = execRequiringZeroAndReturningStdout(loc,
                BashCommands.pipeTextTo("hello world\n"+"and goodbye\n", "wc")).get();

        assertEquals(Strings.replaceAllRegex(output, "\\s+", " ").trim(), "3 4 25");
    }

    @Test(groups="Integration")
    public void testWaitForFileContentsWhenAbortingOnFail() throws Exception {
        String fileContent = "mycontents";
        String cmd = BashCommands.waitForFileContents(destFile.getAbsolutePath(), fileContent, Duration.ONE_SECOND, true);

        int exitcode = loc.execCommands("test", ImmutableList.of(cmd));
        assertEquals(exitcode, 1);
        
        Files.write(fileContent, destFile, Charsets.UTF_8);
        int exitcode2 = loc.execCommands("test", ImmutableList.of(cmd));
        assertEquals(exitcode2, 0);
    }

    @Test(groups="Integration")
    public void testWaitForFileContentsWhenNotAbortingOnFail() throws Exception {
        String fileContent = "mycontents";
        String cmd = BashCommands.waitForFileContents(destFile.getAbsolutePath(), fileContent, Duration.ONE_SECOND, false);

        String output = execRequiringZeroAndReturningStdout(loc, cmd).get();
        assertTrue(output.contains("Couldn't find"), "output="+output);

        Files.write(fileContent, destFile, Charsets.UTF_8);
        String output2 = execRequiringZeroAndReturningStdout(loc, cmd).get();
        assertFalse(output2.contains("Couldn't find"), "output="+output2);
    }
    
    @Test(groups="Integration")
    public void testWaitForFileContentsWhenContentsAppearAfterStart() throws Exception {
        String fileContent = "mycontents";

        String cmd = BashCommands.waitForFileContents(destFile.getAbsolutePath(), fileContent, Duration.THIRTY_SECONDS, false);
        ProcessTaskWrapper<String> t = execRequiringZeroAndReturningStdout(loc, cmd);
        exec.submit(t);
        
        // sleep for long enough to ensure the ssh command is definitely executing
        Thread.sleep(5*1000);
        assertFalse(t.isDone());
        
        Files.write(fileContent, destFile, Charsets.UTF_8);
        String output = t.get();
        assertFalse(output.contains("Couldn't find"), "output="+output);
    }
    
    @Test(groups="Integration", dependsOnMethods="testSudo")
    public void testWaitForPortFreeWhenAbortingOnTimeout() throws Exception {
        ServerSocket serverSocket = openServerSocket();
        try {
            int port = serverSocket.getLocalPort();
            String cmd = BashCommands.waitForPortFree(port, Duration.ONE_SECOND, true);
    
            int exitcode = loc.execCommands("test", ImmutableList.of(cmd));
            assertEquals(exitcode, 1);
            
            serverSocket.close();
            assertTrue(Networking.isPortAvailable(port));
            int exitcode2 = loc.execCommands("test", ImmutableList.of(cmd));
            assertEquals(exitcode2, 0);
        } finally {
            serverSocket.close();
        }
    }

    @Test(groups="Integration", dependsOnMethods="testSudo")
    public void testWaitForPortFreeWhenNotAbortingOnTimeout() throws Exception {
        ServerSocket serverSocket = openServerSocket();
        try {
            int port = serverSocket.getLocalPort();
            String cmd = BashCommands.waitForPortFree(port, Duration.ONE_SECOND, false);
    
            String output = execRequiringZeroAndReturningStdout(loc, cmd).get();
            assertTrue(output.contains(port+" still in use"), "output="+output);
    
            serverSocket.close();
            assertTrue(Networking.isPortAvailable(port));
            String output2 = execRequiringZeroAndReturningStdout(loc, cmd).get();
            assertFalse(output2.contains("still in use"), "output="+output2);
        } finally {
            serverSocket.close();
        }
    }
    
    @Test(groups="Integration", dependsOnMethods="testSudo")
    public void testWaitForPortFreeWhenFreedAfterStart() throws Exception {
        ServerSocket serverSocket = openServerSocket();
        try {
            int port = serverSocket.getLocalPort();
    
            String cmd = BashCommands.waitForPortFree(port, Duration.THIRTY_SECONDS, false);
            ProcessTaskWrapper<String> t = execRequiringZeroAndReturningStdout(loc, cmd);
            exec.submit(t);
            
            // sleep for long enough to ensure the ssh command is definitely executing
            Thread.sleep(5*1000);
            assertFalse(t.isDone());
            
            serverSocket.close();
            assertTrue(Networking.isPortAvailable(port));
            String output = t.get();
            assertFalse(output.contains("still in use"), "output="+output);
        } finally {
            serverSocket.close();
        }
    }
    
    private ProcessTaskWrapper<String> execRequiringZeroAndReturningStdout(SshMachineLocation loc, String... cmds) {
        ProcessTaskWrapper<String> t = SshTasks.newSshExecTaskFactory(loc, cmds)
                .requiringZeroAndReturningStdout().newTask();
        exec.submit(t);
        return t;
    }
    private ServerSocket openServerSocket() {
        int lowerBound = 40000;
        int upperBound = 40100;
        for (int i = lowerBound; i < upperBound; i++) {
            try {
                return new ServerSocket(i);
            } catch (IOException e) {
                // try next number
            }
        }
        throw new IllegalStateException("No ports available in range "+lowerBound+" to "+upperBound);
    }
}
