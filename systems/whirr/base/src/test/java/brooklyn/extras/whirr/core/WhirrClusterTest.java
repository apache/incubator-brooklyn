package brooklyn.extras.whirr.core;

import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.test.entity.TestApplication;

public class WhirrClusterTest {

    private TestApplication app;
    private WhirrCluster entity;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        entity = app.createAndManageChild(EntitySpec.create(WhirrCluster.class));
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }
    
    @Test
    public void testControllerInitialized() {
        Assert.assertNotNull(entity.getController());
    }
    
}
