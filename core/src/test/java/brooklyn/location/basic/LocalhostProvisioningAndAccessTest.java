package brooklyn.location.basic;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.util.Arrays;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import brooklyn.config.BrooklynProperties;
import brooklyn.location.Location;
import brooklyn.management.internal.LocalManagementContext;

public class LocalhostProvisioningAndAccessTest {

    @AfterMethod(alwaysRun = true)
    public void tearDown(){
        LocalManagementContext.terminateAll();
    }

    @Test(groups="Integration")
    public void testProvisionAndConnect() throws Exception {
        LocalManagementContext mgmt = new LocalManagementContext(BrooklynProperties.Factory.newDefault());
        Location location = mgmt.getLocationRegistry().resolve("localhost");
        assertTrue(location instanceof LocalhostMachineProvisioningLocation);
        SshMachineLocation m = ((LocalhostMachineProvisioningLocation)location).obtain();
        int result = m.execCommands("test", Arrays.asList("echo hello world"));
        assertEquals(result, 0);
    }
    
}
