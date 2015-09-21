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

import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.Lists;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.mgmt.HasTaskChildren;
import org.apache.brooklyn.api.mgmt.Task;
import org.apache.brooklyn.core.entity.BrooklynConfigKeys;
import org.apache.brooklyn.core.entity.factory.ApplicationBuilder;
import org.apache.brooklyn.core.internal.BrooklynProperties;
import org.apache.brooklyn.core.mgmt.BrooklynTaskTags;
import org.apache.brooklyn.core.test.BrooklynAppLiveTestSupport;
import org.apache.brooklyn.core.test.entity.LocalManagementContextForTests;
import org.apache.brooklyn.core.test.entity.TestApplication;
import org.apache.brooklyn.util.core.task.TaskPredicates;
import org.apache.brooklyn.util.text.StringPredicates;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeMethod;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.testng.Assert.assertTrue;

public abstract class AbstractSoftwareProcessStreamsTest extends BrooklynAppLiveTestSupport {
    private static final Logger log = LoggerFactory.getLogger(AbstractSoftwareProcessStreamsTest.class);

    public abstract void testGetsStreams();

    protected abstract Map<String, String> getCommands();

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        if (mgmt!=null) {
            app = ApplicationBuilder.newManagedApp(TestApplication.class, mgmt);
        } else {
            mgmt = new LocalManagementContextForTests(BrooklynProperties.Factory.newDefault());
            EntitySpec<TestApplication> appSpec = EntitySpec.create(TestApplication.class)
                    .configure(BrooklynConfigKeys.SKIP_ON_BOX_BASE_DIR_RESOLUTION, true);
            app = ApplicationBuilder.newManagedApp(appSpec, mgmt);
        }
    }

    public static String getStreamOrFail(Task<?> task, String streamType) {
        String msg = "task="+task+"; stream="+streamType;
        BrooklynTaskTags.WrappedStream stream = checkNotNull(BrooklynTaskTags.stream(task, streamType), "Stream null: " + msg);
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

    protected <T extends SoftwareProcess> void assertStreams(T softwareProcessEntity) {
        Set<Task<?>> tasks = BrooklynTaskTags.getTasksInEntityContext(mgmt.getExecutionManager(), softwareProcessEntity);

        for (Map.Entry<String, String> entry : getCommands().entrySet()) {
            String taskNameRegex = entry.getKey();
            String echoed = entry.getValue();

            Task<?> subTask = findTaskOrSubTask(tasks, TaskPredicates.displayNameMatches(StringPredicates.matchesRegex(taskNameRegex))).get();

            String stdin = getStreamOrFail(subTask, BrooklynTaskTags.STREAM_STDIN);
            String stdout = getStreamOrFail(subTask, BrooklynTaskTags.STREAM_STDOUT);
            String stderr = getStreamOrFail(subTask, BrooklynTaskTags.STREAM_STDERR);
//            String env = getStreamOrFail(subTask, BrooklynTaskTags.STREAM_ENV);
            String msg = "stdin="+stdin+"; stdout="+stdout+"; stderr="+stderr; //+"; env="+env;

            assertTrue(stdin.contains("echo "+echoed), msg);
            assertTrue(stdout.contains(echoed), msg);
        }
    }
}
