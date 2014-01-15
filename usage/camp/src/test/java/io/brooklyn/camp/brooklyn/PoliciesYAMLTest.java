package io.brooklyn.camp.brooklyn;

import java.io.Reader;
import java.util.Map;
import java.util.Set;

import io.brooklyn.camp.spi.Assembly;
import io.brooklyn.camp.spi.AssemblyTemplate;
import io.brooklyn.camp.spi.PlatformRootSummary;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.BrooklynTasks;
import brooklyn.entity.basic.Entities;
import brooklyn.management.ManagementContext;
import brooklyn.management.Task;
import brooklyn.policy.Policy;
import brooklyn.test.policy.TestPolicy;
import brooklyn.util.ResourceUtils;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.stream.Streams;

@Test
public class PoliciesYAMLTest {
    private static final Logger log = LoggerFactory.getLogger(PoliciesYAMLTest.class);

    private ManagementContext brooklynMgmt;
    private BrooklynCampPlatform platform;

    @BeforeMethod(alwaysRun = true)
    public void setup() {
        BrooklynCampPlatformLauncherNoServer launcher = new BrooklynCampPlatformLauncherNoServer();
        launcher.launch();
        brooklynMgmt = launcher.getBrooklynMgmt();

        platform = new BrooklynCampPlatform(PlatformRootSummary.builder().name("Brooklyn CAMP Platform").build(), brooklynMgmt);
    }

    @AfterMethod
    public void teardown() {
        if (brooklynMgmt != null)
            Entities.destroyAll(brooklynMgmt);
    }
    
    @Test
    public void testWithPolicyDeploy() {
        Reader input = Streams.reader(new ResourceUtils(this).getResourceFromUrl("test-app-with-policy.yaml"));
        AssemblyTemplate at = platform.pdp().registerDeploymentPlan(input);

        try {
            Assembly assembly = at.getInstantiator().newInstance().instantiate(at, platform);
            log.info("Test - created " + assembly);

            final Entity app = brooklynMgmt.getEntityManager().getEntity(assembly.getId());
            log.info("App - " + app);
            Assert.assertEquals(app.getDisplayName(), "test-app-with-policy");

            Set<Task<?>> tasks = BrooklynTasks.getTasksInEntityContext(brooklynMgmt.getExecutionManager(), app);
            log.info("Waiting on " + tasks.size() + " task(s)");
            for (Task<?> t : tasks) {
                t.blockUntilEnded();
            }

            log.info("App started:");
            Entities.dumpInfo(app);

            Assert.assertEquals(app.getPolicies().size(), 1);
            Policy policy = app.getPolicies().iterator().next();
            Assert.assertTrue(policy instanceof TestPolicy);
            Map<?, ?> leftoverProperties = ((TestPolicy)policy).getLeftoverProperties();
            Assert.assertEquals(leftoverProperties.get("policyLiteralValue1"), "Hello");
            Assert.assertEquals(leftoverProperties.get("policyLiteralValue2"), "World");
            Assert.assertEquals(leftoverProperties.size(), 2);
        } catch (Exception e) {
            log.warn("Unable to instantiate " + at + " (rethrowing): " + e);
            throw Exceptions.propagate(e);
        }
    }
}
