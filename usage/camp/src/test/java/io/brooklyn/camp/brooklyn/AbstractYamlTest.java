package io.brooklyn.camp.brooklyn;

import io.brooklyn.camp.spi.Assembly;
import io.brooklyn.camp.spi.AssemblyTemplate;
import io.brooklyn.camp.spi.PlatformRootSummary;

import java.io.Reader;
import java.util.Set;

import org.slf4j.Logger;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.BrooklynTasks;
import brooklyn.entity.basic.Entities;
import brooklyn.management.ManagementContext;
import brooklyn.management.Task;
import brooklyn.util.ResourceUtils;
import brooklyn.util.stream.Streams;

public abstract class AbstractYamlTest {

    private ManagementContext brooklynMgmt;
    private BrooklynCampPlatform platform;

    public AbstractYamlTest() {
        super();
    }

    @BeforeMethod(alwaysRun = true)
    public void setup() {
        BrooklynCampPlatformLauncherNoServer launcher = new BrooklynCampPlatformLauncherNoServer();
        launcher.launch();
        brooklynMgmt = launcher.getBrooklynMgmt();
    
        platform = new BrooklynCampPlatform(PlatformRootSummary.builder().name("Brooklyn CAMP Platform").build(), brooklynMgmt);
    }

    @AfterMethod(alwaysRun = true)
    public void teardown() {
        if (brooklynMgmt != null)
            Entities.destroyAll(brooklynMgmt);
    }

    protected void waitForApplicationTasks(Entity app) {
        Set<Task<?>> tasks = BrooklynTasks.getTasksInEntityContext(brooklynMgmt.getExecutionManager(), app);
        getLogger().info("Waiting on " + tasks.size() + " task(s)");
        for (Task<?> t : tasks) {
            t.blockUntilEnded();
        }
    }

    protected Entity createAndStartApplication(String yamlFileName) throws Exception {
        Reader input = Streams.reader(new ResourceUtils(this).getResourceFromUrl(yamlFileName));
        AssemblyTemplate at = platform.pdp().registerDeploymentPlan(input);
        Assembly assembly;
        try {
            assembly = at.getInstantiator().newInstance().instantiate(at, platform);
        } catch (Exception e) {
            getLogger().warn("Unable to instantiate " + at + " (rethrowing): " + e);
            throw e;
        }
        getLogger().info("Test - created " + assembly);
    
        final Entity app = brooklynMgmt.getEntityManager().getEntity(assembly.getId());
        getLogger().info("App - " + app);
        return app;
    }

    protected abstract Logger getLogger();
}