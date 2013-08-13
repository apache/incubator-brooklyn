package brooklyn.location.basic;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.util.NoSuchElementException;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.location.Location;
import brooklyn.management.internal.LocalManagementContext;

public class LocalhostResolverTest {

    private LocalManagementContext managementContext;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        managementContext = new LocalManagementContext();
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown(){
        LocalManagementContext.terminateAll();
    }

    @Test
    public void testThrowsOnInvalid() throws Exception {
        assertThrowsNoSuchElement("wrongprefix");
        assertThrowsIllegalArgument("localhost:(name=abc"); // no closing bracket
        assertThrowsIllegalArgument("localhost:(name)"); // no value for name
        assertThrowsIllegalArgument("localhost:(name=)"); // no value for name
    }
    
    @Test
    public void testResolvesName() throws Exception {
        Location location = resolve("localhost");
        assertTrue(location instanceof LocalhostMachineProvisioningLocation);
        assertEquals(location.getDisplayName(), "localhost");

        Location location2 = resolve("localhost:()");
        assertTrue(location2 instanceof LocalhostMachineProvisioningLocation);
        assertEquals(location2.getDisplayName(), "localhost");

        Location location3 = resolve("localhost:(name=myname)");
        assertTrue(location3 instanceof LocalhostMachineProvisioningLocation);
        assertEquals(location3.getDisplayName(), "myname");
    }
    
    private void assertThrowsNoSuchElement(String val) {
        try {
            resolve(val);
            fail();
        } catch (NoSuchElementException e) {
            // success
        }
    }
    
    private void assertThrowsIllegalArgument(String val) {
        try {
            resolve(val);
            fail();
        } catch (IllegalArgumentException e) {
            // success
        }
    }
    
    private Location resolve(String val) {
        return managementContext.getLocationRegistry().resolve(val);
    }
}
