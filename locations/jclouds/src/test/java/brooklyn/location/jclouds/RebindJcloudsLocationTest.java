package brooklyn.location.jclouds;

import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNull;

import org.jclouds.domain.LoginCredentials;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.rebind.RebindTestFixtureWithApp;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.config.ConfigBag;

import com.google.common.net.HostAndPort;

public class RebindJcloudsLocationTest extends RebindTestFixtureWithApp {

    public static final String LOC_SPEC = "jclouds:aws-ec2:us-east-1";

    private JcloudsLocation origLoc;
    
    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        origLoc = (JcloudsLocation) origManagementContext.getLocationRegistry().resolve(LOC_SPEC);
    }

    // Previously, the rebound config contained "id" which was then passed to createTemporarySshMachineLocation, causing
    // that to fail (because the LocationSpec should not have had "id" in its config)
    @Test
    public void testReboundConfigDoesNotContainId() throws Exception {
        rebind();
        
        JcloudsLocation newLoc = (JcloudsLocation) newManagementContext.getLocationManager().getLocation(origLoc.getId());
        
        ConfigBag newLocConfig = newLoc.getAllConfigBag();
        ConfigBag config = ConfigBag.newInstanceCopying(newLocConfig);
        
        assertNull(newLocConfig.getStringKey(("id")));
        
        SshMachineLocation tempMachine = newLoc.createTemporarySshMachineLocation(
                HostAndPort.fromParts("localhost", 1234), 
                LoginCredentials.builder().identity("myuser").password("mypass").noPrivateKey().build(), 
                config);
        assertNotEquals(tempMachine.getId(), newLoc.getId());
    }
}
