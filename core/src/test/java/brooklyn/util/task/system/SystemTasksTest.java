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
package brooklyn.util.task.system;

import java.io.File;

import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.Entities;
import brooklyn.management.ManagementContext;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.util.os.Os;
import brooklyn.util.task.ssh.SshTasks;

/**
 * Some tests for {@link SystemTasks}. See {@link SshTasks}.
 */
public class SystemTasksTest {

    ManagementContext mgmt;
    File tempDir;
    
    boolean failureExpected;

    @BeforeMethod(alwaysRun=true)
    public void setup() throws Exception {
        mgmt = new LocalManagementContext();
        
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
        ProcessTaskWrapper<T> t = tf.newTask();
        mgmt.getExecutionManager().submit(t);
        return t;
    }

    @Test(groups="Integration")
    public void testExecEchoHello() {
        ProcessTaskWrapper<Integer> t = submit(SystemTasks.exec("sleep 1 ; echo hello world"));
        Assert.assertFalse(t.isDone());
        Assert.assertEquals(t.get(), (Integer)0);
        Assert.assertEquals(t.getTask().getUnchecked(), (Integer)0);
        Assert.assertEquals(t.getStdout().trim(), "hello world");
    }

    // FIXME Behaviour of Bash shell changes from 3.x to 4.x so test is disabled
    @Test(groups="Integration", enabled=false)
    public void testSubshellExitScriptDoesNotExit() {
        checkSubshellExitDoesNotExit(taskSubshellExit().runAsScript());
    }

    @Test(groups="Integration")
    public void testSubshellExitCommandDoesNotExit() {
        checkSubshellExitDoesNotExit(taskSubshellExit().runAsCommand());
    }

    public ProcessTaskFactory<Integer> taskSubshellExit() {
        return SystemTasks.exec("echo hello", "( exit 1 )", "echo bye code $?");
    }

    public void checkSubshellExitDoesNotExit(ProcessTaskFactory<Integer> task) {
        ProcessTaskWrapper<Integer> t = submit(task);
        t.block();
        Assert.assertEquals(t.get(), (Integer)0);
        Assert.assertTrue(t.getStdout().contains("bye code 1"), "stdout is: "+t.getStdout());
    }

    @Test(groups="Integration")
    public void testGroupExitScriptDoesNotExit() {
        checkGroupExitDoesExit(taskGroupExit().runAsScript());
    }

    @Test(groups="Integration")
    public void testGroupExitCommandDoesNotExit() {
        checkGroupExitDoesExit(taskGroupExit().runAsCommand());
    }

    public ProcessTaskFactory<Integer> taskGroupExit() {
        return SystemTasks.exec("echo hello", "{ exit 1 ; }", "echo bye code $?");
    }

    public void checkGroupExitDoesExit(ProcessTaskFactory<Integer> task) {
        ProcessTaskWrapper<Integer> t = submit(task);
        t.block();
        Assert.assertEquals(t.get(), (Integer)1);
        Assert.assertFalse(t.getStdout().contains("bye"), "stdout is: "+t.getStdout());
    }

}
