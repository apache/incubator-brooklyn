package brooklyn.location.basic.jclouds

import static org.testng.Assert.*

import org.testng.annotations.Test

import brooklyn.config.BrooklynProperties
import brooklyn.location.basic.SshMachineLocation

/** asserts successful creation of a VM based on an image with the name "linux-no-firewall"
 *  (you must create the image manually first) */
public class RackspaceUkLocationWithImageNameLiveTest {
    
    private static final String PROVIDER = "cloudservers-uk"
    
    @Test(groups = "Live")
    public void withImageNamePattern() {
        BrooklynProperties props = BrooklynProperties.Factory.newDefault();
        props["brooklyn.jclouds.cloudservers-uk.image-name-regex"] = "linux-no-firewall";
        props["brooklyn.jclouds.cloudservers-uk.hardware-id"] = "4";  //gives the 80gb disk needed
        JcloudsLocationFactory f = new JcloudsLocationFactory(new CredentialsFromEnv(props, PROVIDER).asMap());
        JcloudsLocation l = f.newLocation(null);
        SshMachineLocation sm = l.obtain();
        l.release(sm);
    }
    
}
