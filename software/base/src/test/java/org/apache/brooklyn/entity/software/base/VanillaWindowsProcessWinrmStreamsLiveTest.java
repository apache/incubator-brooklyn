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
package org.apache.brooklyn.entity.software.base;

import com.google.common.collect.ImmutableMap;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.mgmt.Task;
import org.apache.brooklyn.core.internal.BrooklynProperties;
import org.apache.brooklyn.core.mgmt.BrooklynTaskTags;
import org.apache.brooklyn.core.test.entity.LocalManagementContextForTests;
import org.apache.brooklyn.util.core.task.TaskPredicates;
import org.apache.brooklyn.util.text.StringPredicates;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.testng.Assert.assertTrue;

public class VanillaWindowsProcessWinrmStreamsLiveTest extends VanillaSoftwareProcessStreamsIntegrationTest {
    private Location machine;

    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        mgmt = new LocalManagementContextForTests(BrooklynProperties.Factory.newDefault());
/*
        JcloudsLocation loc = (JcloudsLocation) mgmt.getLocationRegistry().resolve("jclouds:aws-ec2:us-west-2", ImmutableMap.of(
                "inboundPorts", ImmutableList.of(5985, 3389),
                "displayName", "AWS Oregon (Windows)",
                "imageNameRegex", ".*Windows.*2012.*",
                "hardwareId", "m3.medium",
                "useJcloudsSshInit", false));
        machine = (WinRmMachineLocation) loc.obtain();
                */
        // ImmutableMap.of supports at most 5 key pairs
        Map<String, String> config = new HashMap<>();
        config.put("hosts", "52.13.85.118:5985");
        config.put("osFamily", "windows");
        config.put("winrm", "52.13.85.118:5985");
        config.put("user", "Administrator");
        config.put("password", "q$p5g3(JSWs");
        config.put("useJcloudsSshInit", "false");
        config.put("byonIdentity", "dev12");
        machine = mgmt.getLocationRegistry().resolve("byon", config);
    }

    @Test(groups = "Live")
    public void testGetsStreams() {
        Map<String, String> cmds = ImmutableMap.<String, String>builder()
                .put("pre-install-command", "myPreInstall")
                .put("ssh: installing.*", "myInstall")
                .put("post-install-command", "myPostInstall")
                .put("ssh: customizing.*", "myCustomizing")
                .put("pre-launch-command", "myPreLaunch")
                .put("ssh: launching.*", "myLaunch")
                .put("post-launch-command", "myPostLaunch")
                .build();
        VanillaWindowsProcess entity = app.createAndManageChild(EntitySpec.create(VanillaWindowsProcess.class)
                .configure("id", "dev12")
                .configure(VanillaSoftwareProcess.PRE_INSTALL_COMMAND, "echo " + cmds.get("pre-install-command"))
                .configure(VanillaSoftwareProcess.INSTALL_COMMAND, "echo " + cmds.get("ssh: installing.*"))
                .configure(VanillaSoftwareProcess.POST_INSTALL_COMMAND, "echo " + cmds.get("post-install-command"))
                .configure(VanillaSoftwareProcess.CUSTOMIZE_COMMAND, "echo " + cmds.get("ssh: customizing.*"))
                .configure(VanillaSoftwareProcess.PRE_LAUNCH_COMMAND, "echo " + cmds.get("pre-launch-command"))
                .configure(VanillaSoftwareProcess.LAUNCH_COMMAND, "echo " + cmds.get("ssh: launching.*"))
                .configure(VanillaSoftwareProcess.POST_LAUNCH_COMMAND, "echo " + cmds.get("post-launch-command"))
                .configure(VanillaSoftwareProcess.CHECK_RUNNING_COMMAND, "true"));
//        app.start(ImmutableList.of(machine));

        Set<Task<?>> tasks = BrooklynTaskTags.getTasksInEntityContext(mgmt.getExecutionManager(), entity);

        for (Map.Entry<String, String> entry : cmds.entrySet()) {
            String taskNameRegex = entry.getKey();
            String echoed = entry.getValue();

            Task<?> subTask = findTaskOrSubTask(tasks, TaskPredicates.displayNameMatches(StringPredicates.matchesRegex(taskNameRegex))).get();

            String stdin = getStreamOrFail(subTask, BrooklynTaskTags.STREAM_STDIN);
            String stdout = getStreamOrFail(subTask, BrooklynTaskTags.STREAM_STDOUT);
            String stderr = getStreamOrFail(subTask, BrooklynTaskTags.STREAM_STDERR);
            String env = getStreamOrFail(subTask, BrooklynTaskTags.STREAM_ENV);
            String msg = "stdin="+stdin+"; stdout="+stdout+"; stderr="+stderr+"; env="+env;

            assertTrue(stdin.contains("echo "+echoed), msg);
            assertTrue(stdout.contains(echoed), msg);
        }
    }
}
