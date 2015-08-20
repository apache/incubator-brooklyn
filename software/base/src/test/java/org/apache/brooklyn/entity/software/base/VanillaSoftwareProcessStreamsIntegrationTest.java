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

import static com.google.common.base.Preconditions.checkNotNull;
import static org.testng.Assert.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.mgmt.HasTaskChildren;
import org.apache.brooklyn.api.mgmt.Task;
import org.apache.brooklyn.core.mgmt.BrooklynTaskTags;
import org.apache.brooklyn.core.mgmt.BrooklynTaskTags.WrappedStream;
import org.apache.brooklyn.core.test.BrooklynAppUnitTestSupport;
import org.apache.brooklyn.util.core.task.TaskPredicates;
import org.apache.brooklyn.util.text.StringPredicates;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

public class VanillaSoftwareProcessStreamsIntegrationTest extends BrooklynAppUnitTestSupport {

    private static final Logger log = LoggerFactory.getLogger(VanillaSoftwareProcessStreamsIntegrationTest.class);

    private Location localhost;

    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        localhost = app.getManagementContext().getLocationRegistry().resolve("localhost");
    }

    @Test(groups = "Integration")
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
        VanillaSoftwareProcess entity = app.createAndManageChild(EntitySpec.create(VanillaSoftwareProcess.class)
                .configure(VanillaSoftwareProcess.PRE_INSTALL_COMMAND, "echo "+cmds.get("pre-install-command"))
                .configure(VanillaSoftwareProcess.INSTALL_COMMAND, "echo "+cmds.get("ssh: installing.*"))
                .configure(VanillaSoftwareProcess.POST_INSTALL_COMMAND, "echo "+cmds.get("post-install-command"))
                .configure(VanillaSoftwareProcess.CUSTOMIZE_COMMAND, "echo "+cmds.get("ssh: customizing.*"))
                .configure(VanillaSoftwareProcess.PRE_LAUNCH_COMMAND, "echo "+cmds.get("pre-launch-command"))
                .configure(VanillaSoftwareProcess.LAUNCH_COMMAND, "echo "+cmds.get("ssh: launching.*"))
                .configure(VanillaSoftwareProcess.POST_LAUNCH_COMMAND, "echo "+cmds.get("post-launch-command"))
                .configure(VanillaSoftwareProcess.CHECK_RUNNING_COMMAND, "true"));
        app.start(ImmutableList.of(localhost));

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

    protected String getStreamOrFail(Task<?> task, String streamType) {
        String msg = "task="+task+"; stream="+streamType;
        WrappedStream stream = checkNotNull(BrooklynTaskTags.stream(task, BrooklynTaskTags.STREAM_STDIN), "Stream null: "+msg);
        return checkNotNull(stream.streamContents.get(), "Contents null: "+msg);
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
}
