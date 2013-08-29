package brooklyn.entity;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.management.ManagementContext;
import brooklyn.test.entity.TestApplication;

public class BrooklynMgmtContextTestSupport {

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
        if (mgmt != null) Entities.destroyAll(mgmt);
        mgmt = null;
    }
    
}
