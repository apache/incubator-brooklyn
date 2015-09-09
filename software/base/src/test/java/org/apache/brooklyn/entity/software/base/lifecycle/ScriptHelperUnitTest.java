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
package org.apache.brooklyn.entity.software.base.lifecycle;

import com.google.common.collect.ImmutableMap;
import org.apache.brooklyn.api.mgmt.Task;
import org.apache.brooklyn.core.mgmt.BrooklynTaskTags;
import org.apache.brooklyn.entity.software.base.AbstractSoftwareProcessSshDriver;
import org.apache.brooklyn.entity.software.base.DoNothingSoftwareProcessDriver;
import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.apache.brooklyn.util.core.task.BasicExecutionContext;
import org.apache.brooklyn.util.core.task.BasicExecutionManager;
import org.apache.brooklyn.util.core.task.DynamicTasks;
import org.apache.brooklyn.util.text.Strings;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.apache.brooklyn.entity.software.base.VanillaSoftwareProcessStreamsIntegrationTest.getStreamOrFail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ScriptHelperUnitTest {
    private static final String NON_ZERO_CODE_COMMAND = "false";

    @Test
    public void testZeroExitCode() {
        DoNothingSoftwareProcessDriver sshRunner = mock(DoNothingSoftwareProcessDriver.class);

        ScriptHelper scriptHelper = new ScriptHelper(sshRunner, "test-zero-code-task");
        Assert.assertEquals(scriptHelper.executeInternal(), 0, "ScriptHelper doesn't return zero code");
    }

    @Test
    public void testNonZeroExitCode() {
        DoNothingSoftwareProcessDriver sshRunner = mock(DoNothingSoftwareProcessDriver.class);
        when(sshRunner.execute(any(Map.class), any(List.class), any(String.class))).thenReturn(1);

        ScriptHelper scriptHelper = new ScriptHelper(sshRunner, "test-zero-code-task")
                .body.append(NON_ZERO_CODE_COMMAND);
        Assert.assertNotEquals(scriptHelper.executeInternal(), 0, "ScriptHelper return zero code for non-zero code task");
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void testNonZeroExitCodeException() {
        DoNothingSoftwareProcessDriver sshRunner = mock(DoNothingSoftwareProcessDriver.class);
        when(sshRunner.execute(any(Map.class), any(List.class), any(String.class))).thenReturn(1);

        ScriptHelper scriptHelper = new ScriptHelper(sshRunner, "test-zero-code-task")
                .failOnNonZeroResultCode()
                .body.append(NON_ZERO_CODE_COMMAND);
        scriptHelper.executeInternal();
    }

    private final String command = "echo Hello World!";
    private final String output = "Hello World!";
    private final String errorStd = "Error output";

    @Test
    public void testTaskGatherOutput() {
        Task<Integer> task = executeSampleScript(new Function<ScriptHelper, Void>() {
            @Override
            public Void apply(ScriptHelper scriptHelper) {
                return null;
            }
        });

        String stdOut = getStreamOrFail(task, BrooklynTaskTags.STREAM_STDOUT);
        String stdErr = getStreamOrFail(task, BrooklynTaskTags.STREAM_STDERR);
        Assert.assertEquals(stdOut, output);
        Assert.assertEquals(stdErr, errorStd);
    }

    @Test(groups="WIP")
    public void testTaskNoGatherOutput() {
        Task<Integer> task = executeSampleScript(new Function<ScriptHelper, Void>() {
            @Override
            public Void apply(ScriptHelper scriptHelper) {
                scriptHelper.gatherOutput(false);
                return null;
            }
        });

        String stdOut = getStreamOrFail(task, BrooklynTaskTags.STREAM_STDOUT);
        String stdErr = getStreamOrFail(task, BrooklynTaskTags.STREAM_STDERR);
        Assert.assertTrue(Strings.isBlank(stdOut));
        Assert.assertTrue(Strings.isBlank(stdErr));
    }

    private Task<Integer> executeSampleScript(Function<ScriptHelper, Void> visitor) {
        SshMachineLocation sshMachineLocation = new SshMachineLocation(ImmutableMap.of("address", "localhost")) {
            @Override
            public int execScript(Map<String,?> props, String summaryForLogging, List<String> commands, Map<String,?> env) {
                Map<String, Object> props2 = (Map<String, Object>)props;
                ByteArrayOutputStream outputStream = (ByteArrayOutputStream)props2.get("out");
                ByteArrayOutputStream errorStream = (ByteArrayOutputStream)props2.get("err");
                try {
                    outputStream.write(output.getBytes());
                    errorStream.write(errorStd.getBytes());
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return 0;
            }
        };
        AbstractSoftwareProcessSshDriver sshDriver = mock(AbstractSoftwareProcessSshDriver.class);
        when(sshDriver.execute(any(Map.class), any(List.class), any(String.class))).thenCallRealMethod();
        when(sshDriver.getMachine()).thenReturn(sshMachineLocation);

        ScriptHelper scriptHelper = new ScriptHelper(sshDriver, "test");
        scriptHelper.setFlag("logPrefix", "./");
        scriptHelper.body.append(command);
        visitor.apply(scriptHelper);

        Task<Integer> task = scriptHelper.newTask();
        DynamicTasks.TaskQueueingResult<Integer> taskQueueingResult = DynamicTasks.queueIfPossible(task);
        BasicExecutionManager em = new BasicExecutionManager("tests");
        BasicExecutionContext ec = new BasicExecutionContext(em);
        taskQueueingResult.executionContext(ec);
        taskQueueingResult.orSubmitAndBlock();

        return task;
    }
}
