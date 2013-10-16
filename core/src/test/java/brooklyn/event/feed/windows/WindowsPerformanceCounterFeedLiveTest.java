package brooklyn.event.feed.windows;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.Sensors;
import brooklyn.location.Location;
import brooklyn.location.MachineLocation;
import brooklyn.location.MachineProvisioningLocation;
import brooklyn.management.ManagementContext;
import brooklyn.test.EntityTestUtils;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.collections.MutableMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class WindowsPerformanceCounterFeedLiveTest {

    final static AttributeSensor<Double> CPU_IDLE_TIME =
            Sensors.newDoubleSensor("cpu.idleTime", "");
    final static AttributeSensor<Integer> TELEPHONE_LINES =
            Sensors.newIntegerSensor("telephone.lines", "");

    // To run this live test, you must configure a location named WindowsLiveTest, or adapt the LOCATION_SPEC below.
    // The location must provide Windows nodes that are running an SSH server on port 22. The login credentials must
    // be either be auto-detectable or configured in brooklyn.properties in the usual fashion.
    //
    // Here is an example configuration from brooklyn.properties for a pre-configured Windows VM
    // running an SSH server with public key authentication:
    //     brooklyn.location.named.WindowsLiveTest=byon:(hosts="ec2-xx-xxx-xxx-xx.eu-west-1.compute.amazonaws.com")
    //     brooklyn.location.named.WindowsLiveTest.user=Administrator
    //     brooklyn.location.named.WindowsLiveTest.privateKeyFile = ~/.ssh/id_rsa
    //     brooklyn.location.named.WindowsLiveTest.publicKeyFile = ~/.ssh/id_rsa.pub
    // The location must by "byon" or another primitive type. Unfortunately, it's not possible to
    // use a jclouds location, as adding a dependency on brooklyn-locations-jclouds would cause a
    // cyclic dependency.
    private static final String LOCATION_SPEC = "named:WindowsLiveTest";

    private ManagementContext mgmt;
    private TestApplication app;
    private Location loc;
    private EntityLocal entity;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        if (mgmt!=null) {
            app = ApplicationBuilder.newManagedApp(TestApplication.class, mgmt);
        } else {
            app = ApplicationBuilder.newManagedApp(TestApplication.class);
            mgmt = ((EntityInternal)app).getManagementContext();
        }

        Map<String,?> allFlags = MutableMap.<String,Object>builder()
                .put("tags", ImmutableList.of(getClass().getName()))
                .build();
        MachineProvisioningLocation<? extends MachineLocation> provisioningLocation =
                (MachineProvisioningLocation<? extends MachineLocation>)
                        mgmt.getLocationRegistry().resolve(LOCATION_SPEC, allFlags);
        loc = provisioningLocation.obtain(ImmutableMap.of());

        entity = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        app.start(ImmutableList.of(loc));
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (mgmt != null) Entities.destroyAll(mgmt);
        mgmt = null;
    }

    @Test(groups = {"Live"})
    public void testRetrievesPerformanceCounters() throws Exception {
        // We can be pretty sure that a Windows instance in the cloud will have zero telephone lines...
        entity.setAttribute(TELEPHONE_LINES, 42);
        WindowsPerformanceCounterFeed feed = WindowsPerformanceCounterFeed.builder()
                .entity(entity)
                .addSensor("\\Processor(_total)\\% Idle Time", CPU_IDLE_TIME)
                .addSensor("\\Telephony\\Lines", TELEPHONE_LINES)
                .build();
        try {
            EntityTestUtils.assertAttributeEqualsEventually(entity, TELEPHONE_LINES, 0);
        } finally {
            feed.stop();
        }
    }

}
