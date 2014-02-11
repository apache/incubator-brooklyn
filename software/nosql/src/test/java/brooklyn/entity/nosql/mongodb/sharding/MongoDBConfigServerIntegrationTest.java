package brooklyn.entity.nosql.mongodb.sharding;

import static org.testng.Assert.assertFalse;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.nosql.mongodb.MongoDBTestHelper;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.trait.Startable;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.test.Asserts;
import brooklyn.test.EntityTestUtils;
import brooklyn.test.entity.TestApplication;

import com.google.common.collect.ImmutableList;

public class MongoDBConfigServerIntegrationTest {
    private TestApplication app;
    private LocalhostMachineProvisioningLocation localhostProvisioningLocation;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        localhostProvisioningLocation = new LocalhostMachineProvisioningLocation();
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }
    
    @Test(groups = "Integration")
    public void testCanStartAndStop() throws Exception {
        MongoDBConfigServer entity = app.createAndManageChild(EntitySpec.create(MongoDBConfigServer.class)
                .configure("mongodbConfTemplateUrl", "classpath:///test-mongodb.conf"));
        app.start(ImmutableList.of(localhostProvisioningLocation));

        EntityTestUtils.assertAttributeEqualsEventually(entity, Startable.SERVICE_UP, true);
        Asserts.assertTrue(MongoDBTestHelper.isConfigServer(entity), "Server is not a config server");
        entity.stop();
        assertFalse(entity.getAttribute(Startable.SERVICE_UP));
    }
}
