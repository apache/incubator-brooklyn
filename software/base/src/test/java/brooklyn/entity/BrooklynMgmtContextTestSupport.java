package brooklyn.entity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.management.ManagementContext;
import brooklyn.test.entity.TestApplication;

public class BrooklynMgmtContextTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(BrooklynMgmtContextTestSupport.class);

    protected TestApplication app;
    protected ManagementContext mgmt;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        if (mgmt!=null) {
            app = ApplicationBuilder.newManagedApp(TestApplication.class, mgmt);
        } else {
            app = ApplicationBuilder.newManagedApp(TestApplication.class);
            mgmt = app.getManagementContext();
        }
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
