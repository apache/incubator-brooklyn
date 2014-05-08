package brooklyn.location.basic;

import static org.testng.Assert.assertTrue;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.Entities;
import brooklyn.location.LocationSpec;
import brooklyn.location.cloud.AvailabilityZoneExtension;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.test.Asserts;
import brooklyn.util.collections.MutableSet;
import brooklyn.util.net.Networking;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class MultiLocationTest {

    private LocalManagementContext managementContext;
    private SshMachineLocation mac1a;
    private SshMachineLocation mac1b;
    private SshMachineLocation mac2a;
    private SshMachineLocation mac2b;
    private FixedListMachineProvisioningLocation<SshMachineLocation> loc1;
    private FixedListMachineProvisioningLocation<SshMachineLocation> loc2;
    private MultiLocation<SshMachineLocation> multiLoc;
    
    @SuppressWarnings("unchecked")
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        managementContext = new LocalManagementContext();
        mac1a = managementContext.getLocationManager().createLocation(LocationSpec.create(SshMachineLocation.class)
                .displayName("mac1a")
                .configure("address", Networking.getInetAddressWithFixedName("1.1.1.1")));
        mac1b = managementContext.getLocationManager().createLocation(LocationSpec.create(SshMachineLocation.class)
                .displayName("mac1b")
                .configure("address", Networking.getInetAddressWithFixedName("1.1.1.2")));
        mac2a = managementContext.getLocationManager().createLocation(LocationSpec.create(SshMachineLocation.class)
                .displayName("mac2a")
                .configure("address", Networking.getInetAddressWithFixedName("1.1.1.3")));
        mac2b = managementContext.getLocationManager().createLocation(LocationSpec.create(SshMachineLocation.class)
                .displayName("mac2b")
                .configure("address", Networking.getInetAddressWithFixedName("1.1.1.4")));
        loc1 = managementContext.getLocationManager().createLocation(LocationSpec.create(FixedListMachineProvisioningLocation.class)
                .displayName("loc1")
                .configure("machines", MutableSet.of(mac1a, mac1b)));
        loc2 = managementContext.getLocationManager().createLocation(LocationSpec.create(FixedListMachineProvisioningLocation.class)
                .displayName("loc2")
                .configure("machines", MutableSet.of(mac2a, mac2b)));
        multiLoc = managementContext.getLocationManager().createLocation(LocationSpec.create(MultiLocation.class)
                        .displayName("multiLoc")
                        .configure("subLocations", ImmutableList.of(loc1, loc2)));
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (managementContext != null) Entities.destroyAll(managementContext);
    }
    
    @Test
    public void testHasAvailabilityZonesAsSubLocations() throws Exception {
        multiLoc.hasExtension(AvailabilityZoneExtension.class);
        AvailabilityZoneExtension extension = multiLoc.getExtension(AvailabilityZoneExtension.class);
        Asserts.assertEqualsIgnoringOrder(extension.getAllSubLocations(), ImmutableList.of(loc1, loc2));
        Asserts.assertEqualsIgnoringOrder(extension.getSubLocations(2), ImmutableList.of(loc1, loc2));
        assertTrue(ImmutableList.of(loc1, loc2).containsAll(extension.getSubLocations(1)));
    }
    
    @Test
    public void testObtainAndReleaseDelegateToSubLocation() throws Exception {
        SshMachineLocation obtained = multiLoc.obtain(ImmutableMap.of());
        assertTrue(ImmutableList.of(mac1a, mac1b, mac2a, mac2b).contains(obtained));
        multiLoc.release(obtained);
    }
}
