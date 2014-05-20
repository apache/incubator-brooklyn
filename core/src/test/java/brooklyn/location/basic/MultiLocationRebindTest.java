package brooklyn.location.basic;

import java.io.File;
import java.util.List;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.rebind.RebindTestUtils;
import brooklyn.location.Location;
import brooklyn.location.LocationSpec;
import brooklyn.location.cloud.AvailabilityZoneExtension;
import brooklyn.management.ManagementContext;
import brooklyn.test.Asserts;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.collections.MutableSet;
import brooklyn.util.net.Networking;

import com.google.common.base.Function;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;

public class MultiLocationRebindTest {

    private ClassLoader classLoader = getClass().getClassLoader();
    private ManagementContext origManagementContext;
    private ManagementContext newManagementContext;
    private File mementoDir;
    
    private TestApplication origApp;
    private TestApplication newApp;
    private SshMachineLocation mac1a;
    private SshMachineLocation mac2a;
    private FixedListMachineProvisioningLocation<SshMachineLocation> loc1;
    private FixedListMachineProvisioningLocation<SshMachineLocation> loc2;
    private MultiLocation<SshMachineLocation> multiLoc;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        mementoDir = Files.createTempDir();
        origManagementContext = RebindTestUtils.newPersistingManagementContext(mementoDir, classLoader, 1);
        origApp = ApplicationBuilder.newManagedApp(EntitySpec.create(TestApplication.class), origManagementContext);
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (origManagementContext != null) Entities.destroyAll(origManagementContext);
        if (newApp != null) Entities.destroyAll(newApp.getManagementContext());
        if (newManagementContext != null) Entities.destroyAll(newManagementContext);
        if (mementoDir != null) RebindTestUtils.deleteMementoDir(mementoDir);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testRebindsMultiLocation() throws Exception {
        mac1a = origManagementContext.getLocationManager().createLocation(LocationSpec.create(SshMachineLocation.class)
                .displayName("mac1a")
                .configure("address", Networking.getInetAddressWithFixedName("1.1.1.1")));
        mac2a = origManagementContext.getLocationManager().createLocation(LocationSpec.create(SshMachineLocation.class)
                .displayName("mac2a")
                .configure("address", Networking.getInetAddressWithFixedName("1.1.1.3")));
        loc1 = origManagementContext.getLocationManager().createLocation(LocationSpec.create(FixedListMachineProvisioningLocation.class)
                .displayName("loc1")
                .configure("machines", MutableSet.of(mac1a)));
        loc2 = origManagementContext.getLocationManager().createLocation(LocationSpec.create(FixedListMachineProvisioningLocation.class)
                .displayName("loc2")
                .configure("machines", MutableSet.of(mac2a)));
        multiLoc = origManagementContext.getLocationManager().createLocation(LocationSpec.create(MultiLocation.class)
                        .displayName("multiLoc")
                        .configure("subLocations", ImmutableList.of(loc1, loc2)));
        
        newApp = rebind();
        newManagementContext = newApp.getManagementContext();
        
        MultiLocation newMultiLoc = (MultiLocation) Iterables.find(newManagementContext.getLocationManager().getLocations(), Predicates.instanceOf(MultiLocation.class));
        AvailabilityZoneExtension azExtension = newMultiLoc.getExtension(AvailabilityZoneExtension.class);
        List<Location> newSublLocs = azExtension.getAllSubLocations();
        Iterable<String> newSubLocNames = Iterables.transform(newSublLocs, new Function<Location, String>() {
            @Override public String apply(Location input) {
                return (input == null) ? null : input.getDisplayName();
            }});
        Asserts.assertEqualsIgnoringOrder(newSubLocNames, ImmutableList.of("loc1", "loc2"));
    }
    
    private TestApplication rebind() throws Exception {
        return rebind(true);
    }
    
    private TestApplication rebind(boolean checkSerializable) throws Exception {
        RebindTestUtils.waitForPersisted(origApp);
        if (checkSerializable) {
            RebindTestUtils.checkCurrentMementoSerializable(origApp);
        }
        return (TestApplication) RebindTestUtils.rebind(mementoDir, getClass().getClassLoader());
    }
}
