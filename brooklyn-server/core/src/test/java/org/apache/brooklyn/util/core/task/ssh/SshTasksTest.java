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
package org.apache.brooklyn.util.core.task.ssh;

import java.io.File;
import java.io.IOException;

import org.apache.brooklyn.api.location.LocationSpec;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.core.effector.ssh.SshEffectorTasksTest;
import org.apache.brooklyn.core.entity.BrooklynConfigKeys;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.mgmt.internal.LocalManagementContext;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.apache.brooklyn.location.localhost.LocalhostMachineProvisioningLocation;
import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.apache.brooklyn.util.core.ssh.BashCommandsIntegrationTest;
import org.apache.brooklyn.util.core.task.ssh.SshFetchTaskFactory;
import org.apache.brooklyn.util.core.task.ssh.SshFetchTaskWrapper;
import org.apache.brooklyn.util.core.task.ssh.SshPutTaskFactory;
import org.apache.brooklyn.util.core.task.ssh.SshPutTaskWrapper;
import org.apache.brooklyn.util.core.task.ssh.SshTasks;
import org.apache.brooklyn.util.core.task.ssh.SshTasksTest;
import org.apache.brooklyn.util.core.task.system.ProcessTaskFactory;
import org.apache.brooklyn.util.core.task.system.ProcessTaskWrapper;
import org.apache.brooklyn.util.net.Urls;
import org.apache.brooklyn.util.os.Os;

/**
 * Some tests for {@link SshTasks}. Note more tests in {@link BashCommandsIntegrationTest}, 
 * {@link SshEffectorTasksTest}, and {@link SoftwareEffectorTest}.
 */
public class SshTasksTest {

    private static final Logger log = LoggerFactory.getLogger(SshTasksTest.class);
    
    ManagementContext mgmt;
    SshMachineLocation host;
    File tempDir;
    
    boolean failureExpected;

    @BeforeMethod(alwaysRun=true)
    public void setup() throws Exception {
        mgmt = new LocalManagementContext();
        
        LocalhostMachineProvisioningLocation lhc = mgmt.getLocationManager().createLocation(LocationSpec.create(LocalhostMachineProvisioningLocation.class));
        host = lhc.obtain();
        clearExpectedFailure();
        tempDir = Os.newTempDir(getClass());
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (mgmt != null) Entities.destroyAll(mgmt);
        mgmt = null;
        tempDir = Os.deleteRecursively(tempDir).asNullOrThrowing();
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


    protected <T> ProcessTaskWrapper<T> submit(final ProcessTaskFactory<T> tf) {
        tf.machine(host);
        ProcessTaskWrapper<T> t = tf.newTask();
        mgmt.getExecutionManager().submit(t);
        return t;
    }

    protected SshPutTaskWrapper submit(final SshPutTaskFactory tf) {
        SshPutTaskWrapper t = tf.newTask();
        mgmt.getExecutionManager().submit(t);
        return t;
    }

    @Test(groups="Integration")
    public void testSshEchoHello() {
        ProcessTaskWrapper<Integer> t = submit(SshTasks.newSshExecTaskFactory(host, "sleep 1 ; echo hello world"));
        Assert.assertFalse(t.isDone());
        Assert.assertEquals(t.get(), (Integer)0);
        Assert.assertEquals(t.getTask().getUnchecked(), (Integer)0);
        Assert.assertEquals(t.getStdout().trim(), "hello world");
    }

    @Test(groups="Integration")
    public void testCopyTo() throws IOException {
        String fn = Urls.mergePaths(tempDir.getPath(), "f1");
        SshPutTaskWrapper t = submit(SshTasks.newSshPutTaskFactory(host, fn).contents("hello world"));
        t.block();
        Assert.assertEquals(FileUtils.readFileToString(new File(fn)), "hello world");
        // and make sure this doesn't throw
        Assert.assertTrue(t.isDone());
        Assert.assertTrue(t.isSuccessful());
        Assert.assertEquals(t.get(), null);
        Assert.assertEquals(t.getExitCode(), (Integer)0);
    }
    
    @Test(groups="Integration")
    public void testCopyToFailBadSubdir() throws IOException {
        String fn = Urls.mergePaths(tempDir.getPath(), "non-existent-subdir/file");
        SshPutTaskWrapper t = submit(SshTasks.newSshPutTaskFactory(host, fn).contents("hello world"));
        //this doesn't fail
        t.block();        
        Assert.assertTrue(t.isDone());
        setExpectingFailure();
        try {
            // but this does
            t.get();
        } catch (Exception e) {
            log.info("The error if file cannot be written is: "+e);
            clearExpectedFailure();
        }
        checkExpectedFailure();
        // and the results indicate failure
        Assert.assertFalse(t.isSuccessful());
        Assert.assertNotNull(t.getException());
        Assert.assertNotEquals(t.getExitCode(), (Integer)0);
    }

    @Test(groups="Integration")
    public void testCopyToFailBadSubdirAllow() throws IOException {
        String fn = Urls.mergePaths(tempDir.getPath(), "non-existent-subdir/file");
        SshPutTaskWrapper t = submit(SshTasks.newSshPutTaskFactory(host, fn).contents("hello world").allowFailure());
        //this doesn't fail
        t.block();        
        Assert.assertTrue(t.isDone());
        // and this doesn't fail either
        Assert.assertEquals(t.get(), null);
        // but it's not successful
        Assert.assertNotNull(t.getException());
        Assert.assertFalse(t.isSuccessful());
        // exit code probably null, but won't be zero
        Assert.assertNotEquals(t.getExitCode(), (Integer)0);
    }

    @Test(groups="Integration")
    public void testCopyToFailBadSubdirCreate() throws IOException {
        String fn = Urls.mergePaths(tempDir.getPath(), "non-existent-subdir-to-create/file");
        SshPutTaskWrapper t = submit(SshTasks.newSshPutTaskFactory(host, fn).contents("hello world").createDirectory());
        t.block();
        // directory should be created, and file readable now
        Assert.assertEquals(FileUtils.readFileToString(new File(fn)), "hello world");
        Assert.assertEquals(t.getExitCode(), (Integer)0);
    }

    @Test(groups="Integration")
    public void testSshFetch() throws IOException {
        String fn = Urls.mergePaths(tempDir.getPath(), "f2");
        FileUtils.write(new File(fn), "hello fetched world");
        
        SshFetchTaskFactory tf = SshTasks.newSshFetchTaskFactory(host, fn);
        SshFetchTaskWrapper t = tf.newTask();
        mgmt.getExecutionManager().submit(t);

        t.block();
        Assert.assertTrue(t.isDone());
        Assert.assertEquals(t.get(), "hello fetched world");
        Assert.assertEquals(t.getBytes(), "hello fetched world".getBytes());
    }

    @Test(groups="Integration")
    public void testSshWithHeaderProperty() {
        host.config().set(BrooklynConfigKeys.SSH_CONFIG_SCRIPT_HEADER, "#!/bin/bash -e\necho foo\n");
        ProcessTaskWrapper<Integer> t = submit(SshTasks.newSshExecTaskFactory(host, "echo bar"));
        Assert.assertTrue(t.block().getStdout().trim().matches("foo\\s+bar"), "mismatched output was: "+t.getStdout());
    }

    @Test(groups="Integration")
    public void testSshIgnoringHeaderProperty() {
        host.config().set(BrooklynConfigKeys.SSH_CONFIG_SCRIPT_HEADER, "#!/bin/bash -e\necho foo\n");
        ProcessTaskWrapper<Integer> t = submit(SshTasks.newSshExecTaskFactory(host, false, "echo bar"));
        Assert.assertTrue(t.block().getStdout().trim().matches("bar"), "mismatched output was: "+t.getStdout());
    }

}
