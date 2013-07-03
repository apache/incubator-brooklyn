package brooklyn.entity.network.bind;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.EntitySpecs;
import brooklyn.location.Location;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.test.EntityTestUtils;
import brooklyn.test.entity.TestApplication;

public class BindDnsServerTest {

    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(BindDnsServerTest.class);

    protected TestApplication app;
    protected Location testLocation;
    protected BindDnsServer dns;

    @BeforeMethod(alwaysRun = true)
    public void setup() throws Exception {
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        testLocation = new LocalhostMachineProvisioningLocation();
    }

    @AfterMethod(alwaysRun = true)
    public void shutdown() {
        Entities.destroyAll(app);
    }

    @Test(groups = { "Integration" })
    protected void testDnsEntity() throws Exception {
        BindDnsServer dns = app.createAndManageChild(EntitySpecs.spec(BindDnsServer.class));

        EntityTestUtils.assertAttributeEqualsEventually(dns, BindDnsServer.SERVICE_UP, true);
    }

}
