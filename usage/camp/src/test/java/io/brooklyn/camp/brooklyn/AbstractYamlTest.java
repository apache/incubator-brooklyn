package io.brooklyn.camp.brooklyn;

import io.brooklyn.camp.spi.Assembly;
import io.brooklyn.camp.spi.AssemblyTemplate;

import java.io.Reader;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.BrooklynTaskTags;
import brooklyn.entity.basic.Entities;
import brooklyn.management.ManagementContext;
import brooklyn.management.Task;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.test.entity.LocalManagementContextForTests;
import brooklyn.util.ResourceUtils;
import brooklyn.util.stream.Streams;

public abstract class AbstractYamlTest {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractYamlTest.class);

    private ManagementContext brooklynMgmt;
    protected BrooklynCampPlatform platform;
    protected BrooklynCampPlatformLauncherNoServer launcher;
    
    public AbstractYamlTest() {
        super();
    }

    protected ManagementContext mgmt() { return brooklynMgmt; }
    
    @BeforeMethod(alwaysRun = true)
    public void setup() {
        launcher = new BrooklynCampPlatformLauncherNoServer() {
            @Override
            protected LocalManagementContext newMgmtContext() {
                return newTestManagementContext();
            }
        };
        launcher.launch();
        brooklynMgmt = launcher.getBrooklynMgmt();
        platform = launcher.getCampPlatform();
    }

    protected LocalManagementContext newTestManagementContext() {
        return new LocalManagementContextForTests();
    }
    
    @AfterMethod(alwaysRun = true)
    public void teardown() {
        if (brooklynMgmt != null) Entities.destroyAll(brooklynMgmt);
        if (launcher != null) launcher.stopServers();
    }

    protected void waitForApplicationTasks(Entity app) {
        Set<Task<?>> tasks = BrooklynTaskTags.getTasksInEntityContext(brooklynMgmt.getExecutionManager(), app);
        getLogger().info("Waiting on " + tasks.size() + " task(s)");
        for (Task<?> t : tasks) {
            t.blockUntilEnded();
        }
    }

    protected Entity createAndStartApplication(String yamlFileName, String ...extraLines) throws Exception {
        String input = new ResourceUtils(this).getResourceAsString(yamlFileName).trim();
        StringBuilder builder = new StringBuilder(input);
        for (String l: extraLines)
            builder.append("\n").append(l);
        return createAndStartApplication(Streams.newReaderWithContents(builder.toString()));
    }

    protected Entity createAndStartApplication(Reader input) throws Exception {
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

    protected Entity createStartWaitAndLogApplication(Reader input) throws Exception {
        Entity app = createAndStartApplication(input);
        waitForApplicationTasks(app);

        getLogger().info("App started:");
        Entities.dumpInfo(app);
        
        return app;
    }

    protected Logger getLogger() {
        return LOG;
    }
}