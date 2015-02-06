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
package brooklyn.entity.software;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.location.LocationSpec;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.management.ManagementContext;
import brooklyn.management.TaskAdaptable;
import brooklyn.management.TaskFactory;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.exceptions.PropagatedRuntimeException;
import brooklyn.util.net.Urls;
import brooklyn.util.task.ssh.SshFetchTaskWrapper;
import brooklyn.util.task.ssh.SshPutTaskWrapper;
import brooklyn.util.task.system.ProcessTaskWrapper;

import com.google.common.io.Files;

public class SshEffectorTasksTest {

    private static final Logger log = LoggerFactory.getLogger(SshEffectorTasksTest.class);
    
    TestApplication app;
    ManagementContext mgmt;
    SshMachineLocation host;
    File tempDir;
    
    boolean failureExpected;

    @BeforeMethod(alwaysRun=true)
    public void setup() throws Exception {
        app = TestApplication.Factory.newManagedInstanceForTests();
        mgmt = app.getManagementContext();
        
        LocalhostMachineProvisioningLocation lhc = mgmt.getLocationManager().createLocation(LocationSpec.create(LocalhostMachineProvisioningLocation.class));
        host = lhc.obtain();
        app.start(Arrays.asList(host));
        clearExpectedFailure();
        tempDir = Files.createTempDir();
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (mgmt != null) Entities.destroyAll(mgmt);
        mgmt = null;
        FileUtils.deleteDirectory(tempDir);
        checkExpectedFailure();
    }

    protected void checkExpectedFailure() {
        if (failureExpected) {
            clearExpectedFailure();
            Assert.fail("Test should have thrown an exception but it did not.");
        }
    }
    
    protected void clearExpectedFailure() {
        failureExpected = false;
    }

    protected void setExpectingFailure() {
        failureExpected = true;
    }
    
    public <T extends TaskAdaptable<?>> T submit(final TaskFactory<T> taskFactory) {
        return Entities.submit(app, taskFactory);
    }
    
    // ------------------- basic ssh
    
    @Test(groups="Integration")
    public void testSshEchoHello() {
        ProcessTaskWrapper<Integer> t = submit(SshEffectorTasks.ssh("sleep 1 ; echo hello world"));
        Assert.assertFalse(t.isDone());
        Assert.assertEquals(t.get(), (Integer)0);
        Assert.assertEquals(t.getTask().getUnchecked(), (Integer)0);
        Assert.assertEquals(t.getStdout().trim(), "hello world");
    }

    @Test(groups="Integration")
    public void testSshPut() throws IOException {
        String fn = Urls.mergePaths(tempDir.getPath(), "f1");
        SshPutTaskWrapper t = submit(SshEffectorTasks.put(fn).contents("hello world"));
        t.block();
        Assert.assertEquals(FileUtils.readFileToString(new File(fn)), "hello world");
        // and make sure this doesn't throw
        Assert.assertTrue(t.isDone());
        Assert.assertTrue(t.isSuccessful());
        Assert.assertEquals(t.get(), null);
        Assert.assertEquals(t.getExitCode(), (Integer)0);
    }

    @Test(groups="Integration")
    public void testSshFetch() throws IOException {
        String fn = Urls.mergePaths(tempDir.getPath(), "f2");
        FileUtils.write(new File(fn), "hello fetched world");
        
        SshFetchTaskWrapper t = submit(SshEffectorTasks.fetch(fn));
        t.block();
        
        Assert.assertTrue(t.isDone());
        Assert.assertEquals(t.get(), "hello fetched world");
    }

    // ----------------- pid stuff
    
    @Test(groups="Integration")
    public void testNonRunningPid() {
        ProcessTaskWrapper<Integer> t = submit(SshEffectorTasks.codePidRunning(99999));
        Assert.assertNotEquals(t.getTask().getUnchecked(), (Integer)0);
        Assert.assertNotEquals(t.getExitCode(), (Integer)0);
        ProcessTaskWrapper<Boolean> t2 = submit(SshEffectorTasks.isPidRunning(99999));
        Assert.assertFalse(t2.getTask().getUnchecked());
    }

    @Test(groups="Integration")
    public void testNonRunningPidRequired() {
        ProcessTaskWrapper<?> t = submit(SshEffectorTasks.requirePidRunning(99999));
        setExpectingFailure();
        try {
            t.getTask().getUnchecked();
        } catch (Exception e) {
            log.info("The error if required PID is not found is: "+e);
            clearExpectedFailure();
            Assert.assertTrue(e.toString().contains("Process with PID"), "Expected nice clue in error but got: "+e);
        }
        checkExpectedFailure();
    }

    public static Integer getMyPid() {
        try {
            java.lang.management.RuntimeMXBean runtime = 
                    java.lang.management.ManagementFactory.getRuntimeMXBean();
            java.lang.reflect.Field jvm = runtime.getClass().getDeclaredField("jvm");
            jvm.setAccessible(true);
//            sun.management.VMManagement mgmt = (sun.management.VMManagement) jvm.get(runtime);
            Object mgmt = jvm.get(runtime);
            java.lang.reflect.Method pid_method =  
                    mgmt.getClass().getDeclaredMethod("getProcessId");
            pid_method.setAccessible(true);

            return (Integer) pid_method.invoke(mgmt);
        } catch (Exception e) {
            throw new PropagatedRuntimeException("Test depends on (fragile) getMyPid method which does not work here", e);
        }
    }

    @Test(groups="Integration")
    public void testRunningPid() {
        ProcessTaskWrapper<Integer> t = submit(SshEffectorTasks.codePidRunning(getMyPid()));
        Assert.assertEquals(t.getTask().getUnchecked(), (Integer)0);
        ProcessTaskWrapper<Boolean> t2 = submit(SshEffectorTasks.isPidRunning(getMyPid()));
        Assert.assertTrue(t2.getTask().getUnchecked());
    }

    @Test(groups="Integration")
    public void testRunningPidFromFile() throws IOException {
        File f = File.createTempFile("testBrooklynPid", ".pid");
        Files.write( (""+getMyPid()).getBytes(), f );
        ProcessTaskWrapper<Integer> t = submit(SshEffectorTasks.codePidFromFileRunning(f.getPath()));
        Assert.assertEquals(t.getTask().getUnchecked(), (Integer)0);
        ProcessTaskWrapper<Boolean> t2 = submit(SshEffectorTasks.isPidFromFileRunning(f.getPath()));
        Assert.assertTrue(t2.getTask().getUnchecked());
    }

    @Test(groups="Integration")
    public void testRequirePidFromFileOnFailure() throws IOException {
        File f = File.createTempFile("testBrooklynPid", ".pid");
        Files.write( "99999".getBytes(), f );
        ProcessTaskWrapper<?> t = submit(SshEffectorTasks.requirePidFromFileRunning(f.getPath()));
        
        setExpectingFailure();
        try {
            t.getTask().getUnchecked();
        } catch (Exception e) {
            log.info("The error if required PID is not found is: "+e);
            clearExpectedFailure();
            Assert.assertTrue(e.toString().contains("Process with PID"), "Expected nice clue in error but got: "+e);
            Assert.assertEquals(t.getExitCode(), (Integer)1);
        }
        checkExpectedFailure();
    }

    @Test(groups="Integration")
    public void testRequirePidFromFileOnFailureNoSuchFile() throws IOException {
        ProcessTaskWrapper<?> t = submit(SshEffectorTasks.requirePidFromFileRunning("/path/does/not/exist/SADVQW"));
        
        setExpectingFailure();
        try {
            t.getTask().getUnchecked();
        } catch (Exception e) {
            log.info("The error if required PID is not found is: "+e);
            clearExpectedFailure();
            Assert.assertTrue(e.toString().contains("Process with PID"), "Expected nice clue in error but got: "+e);
            Assert.assertEquals(t.getExitCode(), (Integer)1);
        }
        checkExpectedFailure();
    }

    @Test(groups="Integration")
    public void testRequirePidFromFileOnFailureTooManyFiles() throws IOException {
        ProcessTaskWrapper<?> t = submit(SshEffectorTasks.requirePidFromFileRunning("/*"));
        
        setExpectingFailure();
        try {
            t.getTask().getUnchecked();
        } catch (Exception e) {
            log.info("The error if required PID is not found is: "+e);
            clearExpectedFailure();
            Assert.assertTrue(e.toString().contains("Process with PID"), "Expected nice clue in error but got: "+e);
            Assert.assertEquals(t.getExitCode(), (Integer)2);
        }
        checkExpectedFailure();
    }

    @Test(groups="Integration")
    public void testRequirePidFromFileOnSuccess() throws IOException {
        File f = File.createTempFile("testBrooklynPid", ".pid");
        Files.write( (""+getMyPid()).getBytes(), f );
        ProcessTaskWrapper<?> t = submit(SshEffectorTasks.requirePidFromFileRunning(f.getPath()));
        
        t.getTask().getUnchecked();
    }

    @Test(groups="Integration")
    public void testRequirePidFromFileOnSuccessAcceptsWildcards() throws IOException {
        File f = File.createTempFile("testBrooklynPid", ".pid");
        Files.write( (""+getMyPid()).getBytes(), f );
        ProcessTaskWrapper<?> t = submit(SshEffectorTasks.requirePidFromFileRunning(f.getPath()+"*"));
        
        t.getTask().getUnchecked();
    }

}
