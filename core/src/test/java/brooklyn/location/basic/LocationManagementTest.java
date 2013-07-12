package brooklyn.location.basic;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.location.LocationSpec;
import brooklyn.management.LocationManager;
import brooklyn.management.ManagementContext;
import brooklyn.test.entity.TestApplication;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;

public class LocationManagementTest {

    private TestApplication app;
    private ManagementContext managementContext;
    private LocationManager locationManager;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        managementContext = app.getManagementContext();
        locationManager = managementContext.getLocationManager();
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() {
        if (app != null) Entities.destroyAll(app);
    }

    @Test
    public void testCreateLocationUsingSpec() {
        SshMachineLocation loc = locationManager.createLocation(LocationSpec.spec(SshMachineLocation.class)
                .configure("address", "1.2.3.4"));
        
        assertEquals(loc.getAddress().getHostAddress(), "1.2.3.4");
        assertSame(locationManager.getLocation(loc.getId()), loc);
    }
    
    @Test
    public void testCreateLocationUsingResolver() {
        String spec = "byon:(hosts=\"1.1.1.1\")";
        FixedListMachineProvisioningLocation<SshMachineLocation> loc = (FixedListMachineProvisioningLocation<SshMachineLocation>) managementContext.getLocationRegistry().resolve(spec);
        SshMachineLocation machine = Iterables.getOnlyElement(loc.getAllMachines());
        
        assertSame(locationManager.getLocation(loc.getId()), loc);
        assertSame(locationManager.getLocation(machine.getId()), machine);
    }
    
    @Test
    public void testChildrenOfManagedLocationAutoManaged() {
        String spec = "byon:(hosts=\"1.1.1.1\")";
        FixedListMachineProvisioningLocation<SshMachineLocation> loc = (FixedListMachineProvisioningLocation<SshMachineLocation>) managementContext.getLocationRegistry().resolve(spec);
        SshMachineLocation machine = new SshMachineLocation(ImmutableMap.of("address", "1.2.3.4"));

        loc.addChild(machine);
        assertSame(locationManager.getLocation(machine.getId()), machine);
        assertTrue(machine.isManaged());
        
        loc.removeChild(machine);
        assertNull(locationManager.getLocation(machine.getId()));
        assertFalse(machine.isManaged());
    }
}
