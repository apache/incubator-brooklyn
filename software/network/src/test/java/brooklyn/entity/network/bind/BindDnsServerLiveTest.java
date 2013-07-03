package brooklyn.entity.network.bind;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.EntitySpecs;
import brooklyn.location.Location;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.test.EntityTestUtils;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.text.Strings;

import com.google.common.collect.ImmutableList;

public class BindDnsServerLiveTest {

    private static final Logger LOG = LoggerFactory.getLogger(BindDnsServerLiveTest.class);

    protected TestApplication app;
    protected Location testLocation;
    protected BindDnsServer dns;

    @BeforeMethod(alwaysRun = true)
    public void setup() throws Exception {
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        testLocation = new LocalhostMachineProvisioningLocation();
    }

    @AfterMethod(alwaysRun = true)
    public void shutdown() throws Exception {
        Entities.destroyAll(app);
        // Thread.sleep(TimeUnit.MINUTES.toMillis(30));
    }

    @DataProvider(name = "virtualMachineData")
    public Object[][] provideVirtualMachineData() {
        return new Object[][] { // ImageId, Provider, Region
            new Object[] { "ami-029f9476", "aws-ec2", "eu-west-1" },
        };
    }

    @Test(groups = "Live", dataProvider = "virtualMachineData")
    protected void testOperatingSystemProvider(String imageId, String provider, String region) throws Exception {
        LOG.info("Testing BIND on {}:{} using {}", new Object[] { provider, region, imageId });

        Map<String, String> properties = MutableMap.of("image-id", region + "/" + imageId, "user", "ec2-user");
        testLocation = app.getManagementContext().getLocationRegistry().resolve(provider + ":" + region, properties);

        BindDnsServer dns = app.createAndManageChild(EntitySpecs.spec(BindDnsServer.class));
        dns.start(ImmutableList.of(testLocation));

        EntityTestUtils.assertAttributeEqualsEventually(dns, BindDnsServer.SERVICE_UP, true);
        Entities.dumpInfo(app);
    }

}
