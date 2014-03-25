package brooklyn.entity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.software.MachineLifecycleEffectorTasks;
import brooklyn.management.internal.ManagementContextInternal;
import brooklyn.test.entity.LocalManagementContextForTests;
import brooklyn.test.entity.TestApplication;

/**
 * To be extended by unit/integration tests.
 * <p>
 * Uses a light-weight management context that will not read {@code ~/.brooklyn/brooklyn.properties}.
 */
public class BrooklynMgmtContextUnitTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(BrooklynMgmtContextUnitTestSupport.class);

    protected TestApplication app;
    protected ManagementContextInternal mgmt;

    protected boolean shouldSkipOnBoxBaseDirResolution() {
        return true;
    }

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        if (mgmt == null) {
            mgmt = new LocalManagementContextForTests();
        }
        EntitySpec<TestApplication> appSpec = EntitySpec.create(TestApplication.class)
                .configure(MachineLifecycleEffectorTasks.SKIP_ON_BOX_BASE_DIR_RESOLUTION, shouldSkipOnBoxBaseDirResolution());
        app = ApplicationBuilder.newManagedApp(appSpec, mgmt);
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        try {
            if (mgmt != null) Entities.destroyAll(mgmt);
        } catch (Throwable t) {
            LOG.error("Caught exception in tearDown method", t);
        } finally {
            mgmt = null;
        }
    }

}
