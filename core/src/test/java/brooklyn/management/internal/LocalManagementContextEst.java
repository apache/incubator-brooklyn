package brooklyn.management.internal;

import static org.testng.Assert.assertEquals;

import org.testng.annotations.Test;

import brooklyn.location.Location;

public class LocalManagementContextEst {
    
    @Test
    public void testReloadProperties() {
        LocalManagementContext context = new LocalManagementContext();
        Location location = context.getLocationRegistry().resolve("localhost");
        assertEquals(location.getDisplayName(), "myname");
        context.reloadBrooklynProperties();
        Location location2 = context.getLocationRegistry().resolve("localhost");
        assertEquals(location.getDisplayName(), "myname");
        assertEquals(location2.getDisplayName(), "myname2");
    }
    
}
