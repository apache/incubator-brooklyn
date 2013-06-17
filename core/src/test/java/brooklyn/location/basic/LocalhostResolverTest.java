package brooklyn.location.basic;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import org.testng.annotations.Test;

import brooklyn.location.Location;

import com.google.common.collect.ImmutableMap;

public class LocalhostResolverTest {

    @Test
    public void testThrowsOnInvalid() throws Exception {
        assertThrowsIllegalArgument("wrongprefix");
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
    
    private void assertThrowsIllegalArgument(String val) {
        try {
            resolve(val);
            fail();
        } catch (IllegalArgumentException e) {
            // success
        }
    }
    
    private Location resolve(String val) {
        return new LocalhostResolver().newLocationFromString(ImmutableMap.of(), val);
    }
}
