package brooklyn.entity.webapp;

import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.test.HttpTestUtils;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.collections.MutableMap;

import com.google.common.collect.ImmutableList;

public class ElasticJavaWebAppServiceIntegrationTest {

    private LocalhostMachineProvisioningLocation loc;
    private TestApplication app;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        loc = new LocalhostMachineProvisioningLocation();
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }

    @Test(groups = "Integration")
    public void testFactory() {
        ElasticJavaWebAppService svc =
            new ElasticJavaWebAppService.Factory().newEntity(MutableMap.of("war", "classpath://hello-world.war"), app);
        Entities.manage(svc);
        app.start(ImmutableList.of(loc));
        
        String url = svc.getAttribute(ElasticJavaWebAppService.ROOT_URL);
        Assert.assertNotNull(url);
        HttpTestUtils.assertContentEventuallyContainsText(url, "Hello");
    }
}
