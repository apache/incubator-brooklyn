package brooklyn.location.basic;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.Entities;
import brooklyn.location.Location;
import brooklyn.location.LocationSpec;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.test.entity.TestEntity;

public class LocationPredicatesTest {

    private LocalManagementContext managementContext;
    private LocalhostMachineProvisioningLocation loc;
    private SshMachineLocation childLoc;
    private Location grandchildLoc;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        managementContext = new LocalManagementContext();
        loc = (LocalhostMachineProvisioningLocation) managementContext.getLocationRegistry().resolve("localhost:(name=mydisplayname)");
        childLoc = loc.obtain();
        grandchildLoc = managementContext.getLocationManager().createLocation(LocationSpec.create(SimulatedLocation.class).parent(childLoc));
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (managementContext != null) Entities.destroyAll(managementContext);
    }
    
    @Test
    public void testIdEqualTo() throws Exception {
        assertTrue(LocationPredicates.idEqualTo(loc.getId()).apply(loc));
        assertFalse(LocationPredicates.idEqualTo("wrongid").apply(loc));
    }
    
    @Test
    public void testConfigEqualTo() throws Exception {
        loc.setConfig(TestEntity.CONF_NAME, "myname");
        assertTrue(LocationPredicates.configEqualTo(TestEntity.CONF_NAME, "myname").apply(loc));
        assertFalse(LocationPredicates.configEqualTo(TestEntity.CONF_NAME, "wrongname").apply(loc));
    }
    
    @Test
    public void testDisplayNameEqualTo() throws Exception {
        assertTrue(LocationPredicates.displayNameEqualTo("mydisplayname").apply(loc));
        assertFalse(LocationPredicates.displayNameEqualTo("wrongname").apply(loc));
    }
    
    @Test
    public void testIsChildOf() throws Exception {
        assertTrue(LocationPredicates.isChildOf(loc).apply(childLoc));
        assertFalse(LocationPredicates.isChildOf(loc).apply(loc));
        assertFalse(LocationPredicates.isChildOf(childLoc).apply(loc));
    }
    
    @Test
    public void testIsDescendantOf() throws Exception {
        assertTrue(LocationPredicates.isDescendantOf(loc).apply(grandchildLoc));
        assertTrue(LocationPredicates.isDescendantOf(loc).apply(childLoc));
        assertFalse(LocationPredicates.isDescendantOf(loc).apply(loc));
        assertFalse(LocationPredicates.isDescendantOf(childLoc).apply(loc));
    }
    
    @Test
    public void testManaged() throws Exception {
        // TODO get exception in LocalhostMachineProvisioningLocation.removeChild because childLoc is "in use";
        // this happens from the call to unmanage(loc), which first unmanaged the children.
        loc.release(childLoc);
        
        assertTrue(LocationPredicates.managed().apply(loc));
        Locations.unmanage(loc);
        assertFalse(LocationPredicates.managed().apply(loc));
    }
}
