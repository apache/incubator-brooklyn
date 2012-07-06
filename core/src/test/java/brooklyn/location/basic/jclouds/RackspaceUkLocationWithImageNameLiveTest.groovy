package brooklyn.location.basic.jclouds

import static org.testng.Assert.*

import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.config.BrooklynProperties
import brooklyn.location.basic.jclouds.JcloudsLocation.JcloudsSshMachineLocation

/** asserts successful creation of a VM based on an image with the name "linux-no-firewall"
 *  (you must create the image manually first) */
public class RackspaceUkLocationWithImageNameLiveTest {
    
    private static final String PROVIDER = "cloudservers-uk"

    BrooklynProperties props
    JcloudsLocationFactory factory
    JcloudsLocation loc
    JcloudsSshMachineLocation machine
    
    @BeforeMethod(groups = "Live")
    public void setUp() {
        props = BrooklynProperties.Factory.newDefault();
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() {
        if (loc != null && machine != null) loc.release(machine)
    }
    
    @Test(groups = "Live")
    public void withImageNamePattern() {
        props["brooklyn.jclouds.cloudservers-uk.image-name-regex"] = ".*Ubuntu 11.10.*";
        props["brooklyn.jclouds.cloudservers-uk.hardware-id"] = "1";  //gives the 80gb disk needed
        factory = new JcloudsLocationFactory(new CredentialsFromEnv(props, PROVIDER).asMap());
        loc = factory.newLocation(null);
        machine = loc.obtain();
    }
    
    /**
     * Tests that can set metadata on the Rackspace VM.
     * <p>
     * Notes:
     * <ul>
     *   <li>This requires a dependency on the mvn org.jclouds.provider:cloudservers-uk module, which is not there by default.
     *       This needs to be added to the core/pom.xml to make this test work!
     *   <li>The image-name-regex was chosen to work with particular account credentials.
     *       Should the "linux-no-firewall" work with all?
     * </ul>
     */
    @Test(groups = "Live")
    public void withVmMetadata() {
        props["brooklyn.jclouds.cloudservers-uk.image-name-regex"] = ".*Ubuntu 11.10.*";
        props["brooklyn.jclouds.cloudservers-uk.hardware-id"] = "1";
        factory = new JcloudsLocationFactory(new CredentialsFromEnv(props, PROVIDER).asMap());
        loc = factory.newLocation(null);
        machine = loc.obtain([userMetadata: [mykey: "myval"]]);
        
        Map<String,String> userMetadata = machine.getNode().getUserMetadata()
        assertEquals(userMetadata, [mykey: "myval"])
    }
}
