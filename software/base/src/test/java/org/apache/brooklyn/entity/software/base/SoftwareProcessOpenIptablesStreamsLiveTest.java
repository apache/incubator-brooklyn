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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.mgmt.HasTaskChildren;
import org.apache.brooklyn.api.mgmt.Task;
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
import org.testng.annotations.Test;

import java.util.Set;

import static org.testng.Assert.assertNotEquals;

public class SoftwareProcessOpenIptablesStreamsLiveTest extends BrooklynAppLiveTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(SoftwareProcessOpenIptablesStreamsLiveTest.class);

    protected BrooklynProperties brooklynProperties;

    protected Location jcloudsLocation;

    protected TestApplication app;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {

        mgmt = new LocalManagementContextForTests(BrooklynProperties.Factory.newDefault());

        jcloudsLocation = mgmt.getLocationRegistry().resolve("jclouds:aws-ec2:us-west-1", ImmutableMap.<String, Object>builder()
                .put("osFamily", "centos")
                .put("osVersionRegex", "6\\..*")
                .put("inboundPorts", ImmutableList.of(22, 31880, 31001, 8080, 8443, 1099))
                .build());

        app = ApplicationBuilder.newManagedApp(newAppSpec(), mgmt);
    }

    @Test(groups = "Live")
    public void testGetsStreams() {
        EmptySoftwareProcess entity = app.createAndManageChild(EntitySpec.create(EmptySoftwareProcess.class)
                .configure(EmptySoftwareProcess.OPEN_IPTABLES, true));

        app.start(ImmutableList.of(jcloudsLocation));
        assertStreams(entity);
    }

    private <T extends SoftwareProcess> void assertStreams(T softwareProcessEntity) {

        Set<Task<?>> tasks = BrooklynTaskTags.getTasksInEntityContext(mgmt.getExecutionManager(), softwareProcessEntity);

        Task<?> subTask = findTaskOrSubTask(tasks, TaskPredicates.displayNameMatches(StringPredicates.matchesRegex("open iptables.*"))).get();

        String stdout = AbstractSoftwareProcessStreamsTest.getStreamOrFail(subTask, BrooklynTaskTags.STREAM_STDOUT);
        String msg = "stdout="+stdout;

        assertNotEquals(stdout, "", msg);
    }

    protected Optional<Task<?>> findTaskOrSubTask(Iterable<? extends Task<?>> tasks, Predicate<? super Task<?>> matcher) {
        Optional<Task<?>> result = findTaskOrSubTaskImpl(tasks, matcher);
        if (!result.isPresent() && LOG.isDebugEnabled()) {
            LOG.debug("Task not found matching "+matcher);
        }
        return result;
    }

    protected Optional<Task<?>> findTaskOrSubTaskImpl(Iterable<? extends Task<?>> tasks, Predicate<? super Task<?>> matcher) {
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
