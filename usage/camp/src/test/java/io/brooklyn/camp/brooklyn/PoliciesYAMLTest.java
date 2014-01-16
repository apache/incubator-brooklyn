package io.brooklyn.camp.brooklyn;

import io.brooklyn.camp.spi.Assembly;
import io.brooklyn.camp.spi.AssemblyTemplate;
import io.brooklyn.camp.spi.PlatformRootSummary;

import java.io.Reader;
import java.util.Map;
import java.util.Set;

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
import brooklyn.util.stream.Streams;

import com.google.common.collect.ImmutableMap;

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

    @AfterMethod(alwaysRun = true)
    public void teardown(){
        if (brooklynMgmt != null)
            Entities.destroyAll(brooklynMgmt);
    }
    
    @Test
    public void testWithAppPolicy() throws Exception {
        Entity app = createAndStartApplication("test-app-with-policy.yaml");
        waitForApplicationTasks(app);
        Assert.assertEquals(app.getDisplayName(), "test-app-with-policy");

        log.info("App started:");
        Entities.dumpInfo(app);

        Assert.assertEquals(app.getPolicies().size(), 1);
        Policy policy = app.getPolicies().iterator().next();
        Assert.assertTrue(policy instanceof TestPolicy);
        Assert.assertEquals(policy.getConfig(TestPolicy.CONF_NAME), "Name from YAML");
        Assert.assertEquals(policy.getConfig(TestPolicy.CONF_FROM_FUNCTION), "$brooklyn: is a fun place");
        Map<?, ?> leftoverProperties = ((TestPolicy) policy).getLeftoverProperties();
        Assert.assertEquals(leftoverProperties.get("policyLiteralValue1"), "Hello");
        Assert.assertEquals(leftoverProperties.get("policyLiteralValue2"), "World");
        Assert.assertEquals(leftoverProperties.size(), 2);
    }
    
    @Test
    public void testWithEntityPolicy() throws Exception {
        Entity app = createAndStartApplication("test-entity-with-policy.yaml");
        waitForApplicationTasks(app);
        Assert.assertEquals(app.getDisplayName(), "test-entity-with-policy");

        log.info("App started:");
        Entities.dumpInfo(app);

        Assert.assertEquals(app.getPolicies().size(), 0);
        Assert.assertEquals(app.getChildren().size(), 1);
        Entity child = app.getChildren().iterator().next();
        Assert.assertEquals(child.getPolicies().size(), 1);
        Policy policy = child.getPolicies().iterator().next();
        Assert.assertNotNull(policy);
        Assert.assertTrue(policy instanceof TestPolicy, "policy=" + policy + "; type=" + policy.getClass());
        Assert.assertEquals(policy.getConfig(TestPolicy.CONF_NAME), "Name from YAML");
        Assert.assertEquals(policy.getConfig(TestPolicy.CONF_FROM_FUNCTION), "$brooklyn: is a fun place");
        Assert.assertEquals(((TestPolicy) policy).getLeftoverProperties(),
                ImmutableMap.of("policyLiteralValue1", "Hello", "policyLiteralValue2", "World"));
    }

    private void waitForApplicationTasks(Entity app) {
        Set<Task<?>> tasks = BrooklynTasks.getTasksInEntityContext(brooklynMgmt.getExecutionManager(), app);
        log.info("Waiting on " + tasks.size() + " task(s)");
        for (Task<?> t : tasks) {
            t.blockUntilEnded();
        }
    }

    private Entity createAndStartApplication(String yamlFileName) throws Exception {
        Reader input = Streams.reader(new ResourceUtils(this).getResourceFromUrl(yamlFileName));
        AssemblyTemplate at = platform.pdp().registerDeploymentPlan(input);
        Assembly assembly;
        try {
            assembly = at.getInstantiator().newInstance().instantiate(at, platform);
        } catch (Exception e) {
            log.warn("Unable to instantiate " + at + " (rethrowing): " + e);
            throw e;
        }
        log.info("Test - created " + assembly);

        final Entity app = brooklynMgmt.getEntityManager().getEntity(assembly.getId());
        log.info("App - " + app);
        return app;
    }
}
