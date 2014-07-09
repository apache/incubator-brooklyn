package io.brooklyn.camp.brooklyn;

import io.brooklyn.camp.spi.Assembly;
import io.brooklyn.camp.spi.AssemblyTemplate;

import java.io.File;
import java.io.Reader;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import brooklyn.entity.Application;
import brooklyn.entity.basic.BrooklynTaskTags;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.rebind.RebindTestUtils;
import brooklyn.management.ManagementContext;
import brooklyn.management.Task;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.util.ResourceUtils;
import brooklyn.util.collections.MutableSet;
import brooklyn.util.stream.Streams;

import com.google.common.io.Files;

@Test
public class JavaWebAppWithDslYamlRebindIntegrationTest extends AbstractYamlTest {
    
    private static final Logger log = LoggerFactory.getLogger(JavaWebAppWithDslYamlRebindIntegrationTest.class);
    
    protected ClassLoader classLoader = getClass().getClassLoader();
    protected File mementoDir;
    protected Set<ManagementContext> mgmtContexts = MutableSet.of();

    @Override
    protected LocalManagementContext newTestManagementContext() {
        if (mementoDir!=null) throw new IllegalStateException("already created mgmt context");
        mementoDir = Files.createTempDir();
        LocalManagementContext mgmt =
            RebindTestUtils.newPersistingManagementContext(mementoDir, classLoader, 1);
        mgmtContexts.add(mgmt);
        return mgmt;
    }
    
    @AfterMethod(alwaysRun = true)
    @Override
    public void teardown() {
        for (ManagementContext mgmt: mgmtContexts) Entities.destroyAll(mgmt);
        super.teardown();
        mementoDir = null;
        mgmtContexts.clear();
    }

    @Override
    protected Logger getLogger() {
        return log;
    }

    public Application rebind(Application app) throws Exception {
        RebindTestUtils.waitForPersisted(app);
        // optionally for good measure can also check this:
//        RebindTestUtils.checkCurrentMementoSerializable(app);
        Application result = RebindTestUtils.rebind(mementoDir, getClass().getClassLoader());
        mgmtContexts.add(result.getManagementContext());
        return result;
    }

    /** as {@link JavaWebAppsIntegrationTest#testWithDbDeploy()} but with rebind */
    @Test(groups="Integration")
    public void testJavaWebAppDeployAndRebind() throws Exception {
        Reader input = Streams.reader(new ResourceUtils(this).getResourceFromUrl("java-web-app-and-db-with-function.yaml"));
        AssemblyTemplate at = platform.pdp().registerDeploymentPlan(input);

        Assembly assembly = at.getInstantiator().newInstance().instantiate(at, platform);
        final Application app = (Application) mgmt().getEntityManager().getEntity(assembly.getId());

        Set<Task<?>> tasks = BrooklynTaskTags.getTasksInEntityContext(mgmt().getExecutionManager(), app);
        for (Task<?> t: tasks) t.blockUntilEnded();
        Entities.dumpInfo(app);

        Application app2 = rebind(app);
        Assert.assertEquals(app2.getChildren().size(), 2);
    }

    // test for https://github.com/brooklyncentral/brooklyn/issues/1422
    @Test(groups="Integration")
    public void testJavaWebWithMemberSpecRebind() throws Exception {
        Reader input = Streams.reader(new ResourceUtils(this).getResourceFromUrl("test-java-web-app-spec-and-db-with-function.yaml"));
        AssemblyTemplate at = platform.pdp().registerDeploymentPlan(input);

        Assembly assembly = at.getInstantiator().newInstance().instantiate(at, platform);
        final Application app = (Application) mgmt().getEntityManager().getEntity(assembly.getId());

        Set<Task<?>> tasks = BrooklynTaskTags.getTasksInEntityContext(mgmt().getExecutionManager(), app);
        for (Task<?> t: tasks) t.blockUntilEnded();
        Entities.dumpInfo(app);
        
        Application app2 = rebind(app);
        Assert.assertEquals(app2.getChildren().size(), 2);
    }

}
