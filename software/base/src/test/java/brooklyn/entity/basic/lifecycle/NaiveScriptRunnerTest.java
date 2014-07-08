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
package brooklyn.entity.basic.lifecycle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.BrooklynTaskTags;
import brooklyn.entity.basic.BrooklynTaskTags.WrappedStream;
import brooklyn.location.NoMachinesAvailableException;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.management.Task;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.repeat.Repeater;
import brooklyn.util.task.BasicExecutionContext;
import brooklyn.util.task.BasicExecutionManager;
import brooklyn.util.task.Tasks;
import brooklyn.util.time.Duration;

import com.google.common.base.Throwables;

@Test
public class NaiveScriptRunnerTest {
    
    private static final Logger log = LoggerFactory.getLogger(NaiveScriptRunnerTest.class);
    
    List<String> commands = new ArrayList<String>();
    
    @BeforeMethod
    private void setup() { commands.clear(); }
    
    private NaiveScriptRunner newMockRunner(final int result) {
        return new NaiveScriptRunner() {
            @Override
            public int execute(List<String> script, String summaryForLogging) {
                return execute(new MutableMap(), script, summaryForLogging);
            }
            @Override
            public int execute(Map flags, List<String> script, String summaryForLogging) {
                commands.addAll(script);
                return result;                
            }
        };
    }

    public static NaiveScriptRunner newLocalhostRunner() {
        return new NaiveScriptRunner() {
            LocalhostMachineProvisioningLocation location = new LocalhostMachineProvisioningLocation();
            @Override
            public int execute(List<String> script, String summaryForLogging) {
                return execute(new MutableMap(), script, summaryForLogging);
            }
            @Override
            public int execute(Map flags, List<String> script, String summaryForLogging) {
                try {
                    Map flags2 = MutableMap.of("logPrefix", "test");
                    flags2.putAll(flags);
                    return location.obtain().execScript(flags2, summaryForLogging, script);
                } catch (NoMachinesAvailableException e) {
                    throw Throwables.propagate(e);
                }
            }
        };
    };

    public void testHeadBodyFootAndResult() {
        ScriptHelper h = new ScriptHelper(newMockRunner(101), "mock");
        int result = h.header.
                append("h1", "h2").body.append("b1", "b2").footer.append("f1", "f2").
                execute();
        Assert.assertEquals(result, 101);
        Assert.assertEquals(commands, Arrays.asList("h1", "h2", "b1", "b2", "f1", "f2"), "List wrong: "+commands);
    }

    public void testFailOnNonZero() {
        ScriptHelper h = new ScriptHelper(newMockRunner(106), "mock");
        boolean succeededWhenShouldntHave = false;
        try {
            h.body.append("ignored").
                failOnNonZeroResultCode().   // will happen
                execute();            
            succeededWhenShouldntHave = true;
        } catch (Exception e) {
            log.info("ScriptHelper non-zero causes return code: "+e);
        }
        if (succeededWhenShouldntHave) Assert.fail("succeeded when shouldn't have");
    }

    public void testFailOnNonZeroDontFailIfZero() {
        int result = new ScriptHelper(newMockRunner(0), "mock").body.append("ignored").
                failOnNonZeroResultCode().   // will happen
                execute();
        Assert.assertEquals(result, 0);
    }


    @Test(groups = "Integration")
    public void testFailingCommandFailsEarly() {
        ScriptHelper script = new ScriptHelper(newLocalhostRunner(), "mock").
                body.append("curl road://to/nowhere", "exit 11").
                gatherOutput();
        int result = script.execute();
        // should get _1_ from curl failing, not 11 from us
        // TODO not sure why though!
        Assert.assertEquals(1, result);
    }

    // TODO a good way to indicate when downloads fail, as that is quite a common case
    // but i think we need quite a bit of scaffolding to detect that problem (without inspecting logs) ...

    @Test(groups = "Integration")
    public void testGatherOutputStdout() {
        ScriptHelper script = new ScriptHelper(newLocalhostRunner(), "mock").
                body.append("echo `echo foo``echo bar`", "exit 8").
                gatherOutput();
        int result = script.execute();
        Assert.assertEquals(8, result);
        if (!script.getResultStdout().contains("foobar"))
            Assert.fail("STDOUT does not contain expected text 'foobar'.\n"+script.getResultStdout()+
                    "\nSTDERR:\n"+script.getResultStderr());
    }

    @Test(groups = "Integration")
    public void testGatherOutputStderr() {
        ScriptHelper script = new ScriptHelper(newLocalhostRunner(), "mock").
                body.append("set -x", "curl road://to/nowhere || exit 11").
                gatherOutput();
        int result = script.execute();
        Assert.assertEquals(11, result);
        if (!script.getResultStderr().contains("road"))
            Assert.fail("STDERR does not contain expected text 'road'.\n"+script.getResultStderr()+
                    "\nSTDOUT:\n"+script.getResultStdout());
    }

    @Test(groups = "Integration")
    public void testGatherOutuputNotEnabled() {
        ScriptHelper script = new ScriptHelper(newLocalhostRunner(), "mock").
                body.append("echo foo", "exit 11");
        int result = script.execute();
        Assert.assertEquals(11, result);
        boolean succeededWhenShouldNotHave = false;
        try {
            script.getResultStdout();
            succeededWhenShouldNotHave = true;
        } catch (Exception e) { /* expected */ }
        if (succeededWhenShouldNotHave) Assert.fail("Should have failed");
    }

    @Test(groups = "Integration")
    public void testStreamsInTask() {
        final ScriptHelper script = new ScriptHelper(newLocalhostRunner(), "mock").
                body.append("echo `echo foo``echo bar`", "grep absent-text badfile_which_does_not_exist_blaahblahasdewq").
                gatherOutput();
        Assert.assertNull(script.peekTask());
        Task<Integer> task = script.newTask();
        Assert.assertTrue(BrooklynTaskTags.streams(task).size() >= 3, "Expected at least 3 streams: "+BrooklynTaskTags.streams(task));
        Assert.assertFalse(Tasks.isQueuedOrSubmitted(task));
        WrappedStream in = BrooklynTaskTags.stream(task, BrooklynTaskTags.STREAM_STDIN);
        Assert.assertNotNull(in);
        Assert.assertTrue(in.streamContents.get().contains("echo foo"), "Expected 'echo foo' but had: "+in.streamContents.get());
        Assert.assertTrue(in.streamSize.get() > 0);
        Assert.assertNotNull(script.peekTask());
    }

    @Test(groups = "Integration")
    public void testAutoQueueAndRuntimeStreamsInTask() {
        final ScriptHelper script = new ScriptHelper(newLocalhostRunner(), "mock").
                body.append("echo `echo foo``echo bar`", "grep absent-text badfile_which_does_not_exist_blaahblahasdewq").
                gatherOutput();
        Task<Integer> submitter = Tasks.<Integer>builder().body(new Callable<Integer>() {
            public Integer call() {
                int result = script.execute();
                return result;
            } 
        }).build();
        BasicExecutionManager em = new BasicExecutionManager("tests");
        BasicExecutionContext ec = new BasicExecutionContext(em);
        try {
            Assert.assertNull(script.peekTask());
            ec.submit(submitter);
            // soon there should be a task which is submitted
            Assert.assertTrue(Repeater.create("get script").every(Duration.millis(10)).limitTimeTo(Duration.FIVE_SECONDS).until(new Callable<Boolean>() {
                public Boolean call() { 
                    return (script.peekTask() != null) && Tasks.isQueuedOrSubmitted(script.peekTask());
                }
            }).run());
            Task<Integer> task = script.peekTask();
            Assert.assertTrue(BrooklynTaskTags.streams(task).size() >= 3, "Expected at least 3 streams: "+BrooklynTaskTags.streams(task));
            // stdin should be populated
            WrappedStream in = BrooklynTaskTags.stream(task, BrooklynTaskTags.STREAM_STDIN);
            Assert.assertNotNull(in);
            Assert.assertTrue(in.streamContents.get().contains("echo foo"), "Expected 'echo foo' but had: "+in.streamContents.get());
            Assert.assertTrue(in.streamSize.get() > 0);
            
            // out and err should exist
            WrappedStream out = BrooklynTaskTags.stream(task, BrooklynTaskTags.STREAM_STDOUT);
            WrappedStream err = BrooklynTaskTags.stream(task, BrooklynTaskTags.STREAM_STDERR);
            Assert.assertNotNull(out);
            Assert.assertNotNull(err);
            
            // it should soon finish, with exit code
            Integer result = task.getUnchecked(Duration.TEN_SECONDS);
            Assert.assertNotNull(result);
            Assert.assertTrue(result > 0, "Expected non-zero exit code: "+result);
            // and should contain foobar in stdout
            if (!script.getResultStdout().contains("foobar"))
                Assert.fail("Script STDOUT does not contain expected text 'foobar'.\n"+script.getResultStdout()+
                        "\nSTDERR:\n"+script.getResultStderr());
            if (!out.streamContents.get().contains("foobar"))
                Assert.fail("Task STDOUT does not contain expected text 'foobar'.\n"+out.streamContents.get()+
                        "\nSTDERR:\n"+script.getResultStderr());
            // and "No such file or directory" in stderr
            if (!script.getResultStderr().contains("No such file or directory"))
                Assert.fail("Script STDERR does not contain expected text 'No such ...'.\n"+script.getResultStdout()+
                        "\nSTDERR:\n"+script.getResultStderr());
            if (!err.streamContents.get().contains("No such file or directory"))
                Assert.fail("Task STDERR does not contain expected text 'No such...'.\n"+out.streamContents.get()+
                        "\nSTDERR:\n"+script.getResultStderr());
        } finally {
            em.shutdownNow();
        }
    }


}
