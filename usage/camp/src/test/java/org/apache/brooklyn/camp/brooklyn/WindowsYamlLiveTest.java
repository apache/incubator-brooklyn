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
package org.apache.brooklyn.camp.brooklyn;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.io.StringReader;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.location.MachineProvisioningLocation;
import org.apache.brooklyn.api.mgmt.HasTaskChildren;
import org.apache.brooklyn.api.mgmt.Task;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.mgmt.BrooklynTaskTags;
import org.apache.brooklyn.core.mgmt.internal.ManagementContextInternal;
import org.apache.brooklyn.entity.software.base.SoftwareProcess;
import org.apache.brooklyn.entity.software.base.VanillaWindowsProcess;
import org.apache.brooklyn.entity.software.base.test.location.WindowsTestFixture;
import org.apache.brooklyn.location.winrm.WinRmMachineLocation;
import org.apache.brooklyn.test.EntityTestUtils;
import org.apache.brooklyn.util.core.task.TaskPredicates;
import org.apache.brooklyn.util.text.StringPredicates;
import org.apache.brooklyn.util.text.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

/**
 * Tests Windows YAML blueprint features.
 */
@Test
public class WindowsYamlLiveTest extends AbstractYamlTest {
    
    // TODO Remove duplication of assertStreams and VanillaWindowsProcessWinrmStreamsLiveTest.assertStreams
    
    private static final Logger log = LoggerFactory.getLogger(WindowsYamlLiveTest.class);

    /**
     * Maps from the task names that are used to the names used in log/exception messages.
     */
    private static final Map<String, String> TASK_REGEX_TO_COMMAND = ImmutableMap.<String, String>builder()
            .put("winrm: pre-install-command.*", "pre-install-command")
            .put("winrm: install.*", "install-command")
            .put("winrm: post-install-command.*", "post-install-command")
            .put("winrm: customize.*", "customize-command")
            .put("winrm: pre-launch-command.*", "pre-launch-command")
            .put("winrm: launch.*", "launch-command")
            .put("winrm: post-launch-command.*", "post-launch-command")
            .put("winrm: stop-command.*", "stop-command")
            .put("winrm: is-running-command.*", "is-running-command")
            .build();

    protected List<String> yamlLocation;
    protected MachineProvisioningLocation<WinRmMachineLocation> location;
    protected WinRmMachineLocation machine;
    protected Entity app;
    
    @BeforeClass(alwaysRun = true)
    public void setUpClass() throws Exception {
        super.setUp();
        
        location = WindowsTestFixture.setUpWindowsLocation(mgmt());
        machine = (WinRmMachineLocation) location.obtain(ImmutableMap.of());
        String ip = machine.getAddress().getHostAddress();
        String password = machine.config().get(WinRmMachineLocation.PASSWORD);

        yamlLocation = ImmutableList.of(
                "location:",
                "  byon:",
                "    hosts:",
                "    - winrm: "+ip+":5985",
                "      password: "+password,
                "      user: Administrator",
                "      osFamily: windows");
    }

    @AfterClass(alwaysRun = true)
    public void tearDownClass() {
        try {
            if (location != null) location.release(machine);
        } catch (Throwable t) {
            log.error("Caught exception in tearDownClass method", t);
        } finally {
            super.tearDown();
        }
    }

    @BeforeMethod(alwaysRun = true)
    @Override
    public void setUp() {
        // no-op; everything done @BeforeClass
    }

    @AfterMethod(alwaysRun = true)
    @Override
    public void tearDown() {
        try {
            if (app != null) Entities.destroy(app);
        } catch (Throwable t) {
            log.error("Caught exception in tearDown method", t);
        } finally {
            app = null;
        }
    }
    
    @Override
    protected ManagementContextInternal mgmt() {
        return (ManagementContextInternal) super.mgmt();
    }
    
    @Test(groups="Live")
    public void testPowershellMinimalist() throws Exception {
        Map<String, String> cmds = ImmutableMap.<String, String>builder()
                .put("myarg", "myval")
                .put("launch.powershell.command", "\"& c:\\\\exit0.ps1\"")
                .put("checkRunning.powershell.command", "\"& c:\\\\exit0.bat\"")
                .build();
        
        Map<String, List<String>> stdouts = ImmutableMap.of();
        
        runWindowsApp(cmds, stdouts, null);
    }
    
    @Test(groups="Live")
    public void testPowershell() throws Exception {
        Map<String, String> cmds = ImmutableMap.<String, String>builder()
                .put("myarg", "myval")
                .put("pre.install.powershell.command", "\"& c:\\\\exit0.ps1\"")
                .put("install.powershell.command", "\"& c:\\\\echoMyArg.ps1 -myarg myInstall\"")
                .put("post.install.powershell.command", "\"& c:\\\\echoArg.bat myPostInstall\"")
                .put("customize.powershell.command", "\"& c:\\\\echoFreemarkerMyarg.bat\"")
                .put("pre.launch.powershell.command", "\"& c:\\\\echoFreemarkerMyarg.ps1\"")
                .put("launch.powershell.command", "\"& c:\\\\exit0.ps1\"")
                .put("post.launch.powershell.command", "\"& c:\\\\exit0.ps1\"")
                .put("checkRunning.powershell.command", "\"& c:\\\\exit0.ps1\"")
                .put("stop.powershell.command", "\"& c:\\\\exit0.ps1\"")
                .build();
        
        Map<String, List<String>> stdouts = ImmutableMap.<String, List<String>>builder()
                .put("winrm: install.*", ImmutableList.of("myInstall"))
                .put("winrm: post-install-command.*", ImmutableList.of("myPostInstall"))
                .put("winrm: customize.*", ImmutableList.of("myval"))
                .put("winrm: pre-launch-command.*", ImmutableList.of("myval"))
                .build();
        
        runWindowsApp(cmds, stdouts, null);
    }
    
    @Test(groups="Live")
    public void testBatch() throws Exception {
        Map<String, String> cmds = ImmutableMap.<String, String>builder()
                .put("myarg", "myval")
                .put("pre.install.command", "\"PowerShell -NonInteractive -NoProfile -Command c:\\\\exit0.ps1\"")
                .put("install.command", "\"PowerShell -NonInteractive -NoProfile -Command c:\\\\echoMyArg.ps1 -myarg myInstall\"")
                .put("post.install.command", "\"c:\\\\echoArg.bat myPostInstall\"")
                .put("customize.command", "\"c:\\\\echoFreemarkerMyarg.bat\"")
                .put("pre.launch.command", "\"PowerShell -NonInteractive -NoProfile -Command c:\\\\echoFreemarkerMyarg.ps1\"")
                .put("launch.command", "\"PowerShell -NonInteractive -NoProfile -Command c:\\\\exit0.ps1\"")
                .put("post.launch.command", "\"PowerShell -NonInteractive -NoProfile -Command c:\\\\exit0.ps1\"")
                .put("checkRunning.command", "\"PowerShell -NonInteractive -NoProfile -Command c:\\\\exit0.ps1\"")
                .put("stop.command", "\"PowerShell -NonInteractive -NoProfile -Command c:\\\\exit0.ps1\"")
                .build();

        Map<String, List<String>> stdouts = ImmutableMap.<String, List<String>>builder()
                .put("winrm: install.*", ImmutableList.of("myInstall"))
                .put("winrm: post-install-command.*", ImmutableList.of("myPostInstall"))
                .put("winrm: customize.*", ImmutableList.of("myval"))
                .put("winrm: pre-launch-command.*", ImmutableList.of("myval"))
                .build();
        
        runWindowsApp(cmds, stdouts, null);
    }
    
    @Test(groups="Live")
    public void testPowershellExit1() throws Exception {
        Map<String, String> cmds = ImmutableMap.<String, String>builder()
                .put("myarg", "myval")
                .put("pre.install.powershell.command", "\"& c:\\\\exit1.ps1\"")
                .put("install.powershell.command", "\"& c:\\\\echoMyArg.ps1 -myarg myInstall\"")
                .put("post.install.powershell.command", "\"& c:\\\\echoArg.bat myPostInstall\"")
                .put("customize.powershell.command", "\"& c:\\\\echoFreemarkerMyarg.bat\"")
                .put("pre.launch.powershell.command", "\"& c:\\\\echoFreemarkerMyarg.ps1\"")
                .put("launch.powershell.command", "\"& c:\\\\exit0.ps1\"")
                .put("post.launch.powershell.command", "\"& c:\\\\exit0.ps1\"")
                .put("checkRunning.powershell.command", "\"& c:\\\\exit0.ps1\"")
                .put("stop.powershell.command", "\"& c:\\\\exit0.ps1\"")
                .build();
        
        Map<String, List<String>> stdouts = ImmutableMap.of();
        
        runWindowsApp(cmds, stdouts, "winrm: pre-install-command.*");
    }
    
    // FIXME Failing to match the expected exception, but looks fine! Needs more investigation.
    @Test(groups="Live")
    public void testPowershellCheckRunningExit1() throws Exception {
        Map<String, String> cmds = ImmutableMap.<String, String>builder()
                .put("myarg", "myval")
                .put("pre.install.powershell.command", "\"& c:\\\\exit0.ps1\"")
                .put("install.powershell.command", "\"& c:\\\\echoMyArg.ps1 -myarg myInstall\"")
                .put("post.install.powershell.command", "\"& c:\\\\echoArg.bat myPostInstall\"")
                .put("customize.powershell.command", "\"& c:\\\\echoFreemarkerMyarg.bat\"")
                .put("pre.launch.powershell.command", "\"& c:\\\\echoFreemarkerMyarg.ps1\"")
                .put("launch.powershell.command", "\"& c:\\\\exit0.ps1\"")
                .put("post.launch.powershell.command", "\"& c:\\\\exit0.ps1\"")
                .put("checkRunning.powershell.command", "\"& c:\\\\exit1.ps1\"")
                .put("stop.powershell.command", "\"& c:\\\\exit0.ps1\"")
                .build();
        
        Map<String, List<String>> stdouts = ImmutableMap.of();
        
        runWindowsApp(cmds, stdouts, "winrm: is-running-command.*");
    }
    
    // FIXME Needs more work to get the stop's task that failed, so can assert got the right error message
    @Test(groups="Live")
    public void testPowershellStopExit1() throws Exception {
        Map<String, String> cmds = ImmutableMap.<String, String>builder()
                .put("myarg", "myval")
                .put("pre.install.powershell.command", "\"& c:\\\\exit0.ps1\"")
                .put("install.powershell.command", "\"& c:\\\\echoMyArg.ps1 -myarg myInstall\"")
                .put("post.install.powershell.command", "\"& c:\\\\echoArg.bat myPostInstall\"")
                .put("customize.powershell.command", "\"& c:\\\\echoFreemarkerMyarg.bat\"")
                .put("pre.launch.powershell.command", "\"& c:\\\\echoFreemarkerMyarg.ps1\"")
                .put("launch.powershell.command", "\"& c:\\\\exit0.ps1\"")
                .put("post.launch.powershell.command", "\"& c:\\\\exit0.ps1\"")
                .put("checkRunning.powershell.command", "\"& c:\\\\exit0.ps1\"")
                .put("stop.powershell.command", "\"& c:\\\\exit1.ps1\"")
                .build();
        
        Map<String, List<String>> stdouts = ImmutableMap.of();
        
        runWindowsApp(cmds, stdouts, "winrm: stop-command.*");
    }
    
    protected void runWindowsApp(Map<String, String> commands, Map<String, List<String>> stdouts, String taskRegexFailed) throws Exception {
        String cmdFailed = (taskRegexFailed == null) ? null : TASK_REGEX_TO_COMMAND.get(taskRegexFailed);
        
        List<String> yaml = Lists.newArrayList();
        yaml.addAll(yamlLocation);
        yaml.addAll(ImmutableList.of(
                "services:",
                "- type: org.apache.brooklyn.entity.software.base.VanillaWindowsProcess",
                "  brooklyn.config:",
                "    onbox.base.dir.skipResolution: true",
                "    templates.preinstall:",
                "      classpath://org/apache/brooklyn/camp/brooklyn/echoFreemarkerMyarg.bat: c:\\echoFreemarkerMyarg.bat",
                "      classpath://org/apache/brooklyn/camp/brooklyn/echoFreemarkerMyarg.ps1: c:\\echoFreemarkerMyarg.ps1",
                "    files.preinstall:",
                "      classpath://org/apache/brooklyn/camp/brooklyn/echoArg.bat: c:\\echoArg.bat",
                "      classpath://org/apache/brooklyn/camp/brooklyn/echoMyArg.ps1: c:\\echoMyArg.ps1",
                "      classpath://org/apache/brooklyn/camp/brooklyn/exit0.bat: c:\\exit0.bat",
                "      classpath://org/apache/brooklyn/camp/brooklyn/exit1.bat: c:\\exit1.bat",
                "      classpath://org/apache/brooklyn/camp/brooklyn/exit0.ps1: c:\\exit0.ps1",
                "      classpath://org/apache/brooklyn/camp/brooklyn/exit1.ps1: c:\\exit1.ps1",
                ""));
        
        for (Map.Entry<String, String> entry : commands.entrySet()) {
            yaml.add("    "+entry.getKey()+": "+entry.getValue());
        }

        if (Strings.isBlank(cmdFailed)) {
            app = createAndStartApplication(new StringReader(Joiner.on("\n").join(yaml)));
            waitForApplicationTasks(app);
            log.info("App started:");
            Entities.dumpInfo(app);
            
            VanillaWindowsProcess entity = (VanillaWindowsProcess) app.getChildren().iterator().next();
            
            EntityTestUtils.assertAttributeEqualsEventually(entity, Attributes.SERVICE_UP, true);
            assertStreams(entity, stdouts);
            
        } else if (cmdFailed.equals("stop-command")) {
            app = createAndStartApplication(new StringReader(Joiner.on("\n").join(yaml)));
            waitForApplicationTasks(app);
            log.info("App started:");
            Entities.dumpInfo(app);
            VanillaWindowsProcess entity = (VanillaWindowsProcess) app.getChildren().iterator().next();
            EntityTestUtils.assertAttributeEqualsEventually(entity, Attributes.SERVICE_UP, true);
            
            entity.stop();
            assertSubTaskFailures(entity, ImmutableMap.of(taskRegexFailed, StringPredicates.containsLiteral("for "+cmdFailed)));
            
        } else {
            try {
                app = createAndStartApplication(new StringReader(Joiner.on("\n").join(yaml)));
                fail("start should have failed for app="+app);
            } catch (Exception e) {
                if (e.toString().contains("invalid result") && e.toString().contains("for "+cmdFailed)) throw e;
            }
        }
    }

    protected void assertStreams(SoftwareProcess entity, Map<String, List<String>> stdouts) {
        Set<Task<?>> tasks = BrooklynTaskTags.getTasksInEntityContext(mgmt().getExecutionManager(), entity);

        for (Map.Entry<String, List<String>> entry : stdouts.entrySet()) {
            String taskNameRegex = entry.getKey();
            List<String> expectedOuts = entry.getValue();

            Task<?> subTask = findTaskOrSubTask(tasks, TaskPredicates.displayNameSatisfies(StringPredicates.matchesRegex(taskNameRegex))).get();

            String stdin = getStreamOrFail(subTask, BrooklynTaskTags.STREAM_STDIN);
            String stdout = getStreamOrFail(subTask, BrooklynTaskTags.STREAM_STDOUT);
            String stderr = getStreamOrFail(subTask, BrooklynTaskTags.STREAM_STDERR);
            String env = getStream(subTask, BrooklynTaskTags.STREAM_ENV);
            String msg = "stdin="+stdin+"; stdout="+stdout+"; stderr="+stderr+"; env="+env;

            for (String expectedOut : expectedOuts) {
                assertTrue(stdout.contains(expectedOut), msg);
            }
        }
    }

    protected void assertSubTaskFailures(SoftwareProcess entity, Map<String, Predicate<CharSequence>> taskErrs) throws Exception {
        Set<Task<?>> tasks = BrooklynTaskTags.getTasksInEntityContext(mgmt().getExecutionManager(), entity);

        for (Map.Entry<String, Predicate<CharSequence>> entry : taskErrs.entrySet()) {
            String taskNameRegex = entry.getKey();
            Predicate<? super String> errChecker = entry.getValue();
            Task<?> subTask = findTaskOrSubTask(tasks, TaskPredicates.displayNameSatisfies(StringPredicates.matchesRegex(taskNameRegex))).get();
            String msg = "regex="+taskNameRegex+"; task="+subTask;
            assertNotNull(subTask, msg);
            assertTrue(subTask.isDone(), msg);
            assertTrue(subTask.isError(), msg);
            try {
                subTask.get();
                fail();
            } catch (Exception e) {
                if (!errChecker.apply(e.toString())) {
                    throw e;
                }
            }
        }
    }

    public static String getStreamOrFail(Task<?> task, String streamType) {
        String msg = "task="+task+"; stream="+streamType;
        BrooklynTaskTags.WrappedStream stream = checkNotNull(BrooklynTaskTags.stream(task, streamType), "Stream null: " + msg);
        return checkNotNull(stream.streamContents.get(), "Contents null: "+msg);
    }

    public static String getStream(Task<?> task, String streamType) {
        BrooklynTaskTags.WrappedStream stream = BrooklynTaskTags.stream(task, streamType);
        return (stream != null) ? stream.streamContents.get() : null;
    }

    protected Optional<Task<?>> findTaskOrSubTask(Iterable<? extends Task<?>> tasks, Predicate<? super Task<?>> matcher) {
        List<String> taskNames = Lists.newArrayList();
        Optional<Task<?>> result = findTaskOrSubTaskImpl(tasks, matcher, taskNames);
        if (!result.isPresent() && log.isDebugEnabled()) {
            log.debug("Task not found matching "+matcher+"; contender names were "+taskNames);
        }
        return result;
    }

    protected Optional<Task<?>> findTaskOrSubTaskImpl(Iterable<? extends Task<?>> tasks, Predicate<? super Task<?>> matcher, List<String> taskNames) {
        for (Task<?> task : tasks) {
            if (matcher.apply(task)) return Optional.<Task<?>>of(task);

            if (!(task instanceof HasTaskChildren)) {
                return Optional.absent();
            } else {
                Optional<Task<?>> subResult = findTaskOrSubTask(((HasTaskChildren) task).getChildren(), matcher);
                if (subResult.isPresent()) return subResult;
            }
        }

        return Optional.<Task<?>>absent();
    }

    @Override
    protected Logger getLogger() {
        return log;
    }
}
