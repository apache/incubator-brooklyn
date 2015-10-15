package org.apache.brooklyn.entity.software.base;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
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

import java.util.List;
import java.util.Set;

import static org.testng.Assert.assertNotEquals;

public class EmptySoftwareProcessStreamsLiveTest extends BrooklynAppLiveTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(EmptySoftwareProcessStreamsLiveTest.class);

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

//        jcloudsLocation = mgmt.getLocationRegistry().resolve("byon", ImmutableMap.of("hosts", "54.177.12.52:22",
//                "user", "Ivana", "privateKeyFile", "C:\\Users\\Ivana\\.ssh\\id_vms",
//                "inboundPorts", "22,31880,31001,8080,8443,1099"));

        app = ApplicationBuilder.newManagedApp(newAppSpec(), mgmt);
    }

    @Test
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

    protected com.google.common.base.Optional<Task<?>> findTaskOrSubTask(Iterable<? extends Task<?>> tasks, com.google.common.base.Predicate<? super Task<?>> matcher) {
        List<String> taskNames = Lists.newArrayList();
        com.google.common.base.Optional<Task<?>> result = findTaskOrSubTaskImpl(tasks, matcher, taskNames);
        if (!result.isPresent() && LOG.isDebugEnabled()) {
            LOG.debug("Task not found matching "+matcher+"; contender names were "+taskNames);
        }
        return result;
    }

    protected com.google.common.base.Optional<Task<?>> findTaskOrSubTaskImpl(Iterable<? extends Task<?>> tasks, com.google.common.base.Predicate<? super Task<?>> matcher, List<String> taskNames) {
        for (Task<?> task : tasks) {
            if (matcher.apply(task)) return com.google.common.base.Optional.<Task<?>>of(task);

            if (!(task instanceof HasTaskChildren)) {
                return com.google.common.base.Optional.absent();
            } else {
                com.google.common.base.Optional<Task<?>> subResult = findTaskOrSubTask(((HasTaskChildren) task).getChildren(), matcher);
                if (subResult.isPresent()) return subResult;
            }
        }

        return com.google.common.base.Optional.<Task<?>>absent();
    }
}
