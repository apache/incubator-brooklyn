package brooklyn.location.basic.jclouds

import static org.testng.Assert.*

import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.basic.Entities
import brooklyn.location.basic.jclouds.JcloudsLocation.JcloudsSshMachineLocation
import brooklyn.management.ManagementContext

import com.google.common.collect.ImmutableMap

/** asserts successful creation of a VM based on an image with the name "linux-no-firewall"
 *  (you must create the image manually first) */
public class RackspaceUkLocationWithImageNameLiveTest {
    
    private static final String PROVIDER = "cloudservers-uk"

    private ManagementContext managementContext;
    JcloudsLocation loc
    JcloudsSshMachineLocation machine
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() {
        managementContext = Entities.newManagementContext(
            ImmutableMap.builder()
            .put("provider", PROVIDER)
            .put("brooklyn.jclouds.cloudservers-uk.image-name-regex", ".*Ubuntu 11.10.*")
            .put("brooklyn.jclouds.cloudservers-uk.hardware-id", "1")  //gives the 80gb disk needed
            .build());
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() {
        if (loc != null && machine != null) loc.release(machine)
    }
    
    @Test(groups = "Live")
    public void withImageNamePattern() {
        loc = managementContext.getLocationRegistry().resolve(PROVIDER);
        machine = loc.obtain();
        assertTrue(machine.isSshable(), "machine="+machine)
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
        loc = managementContext.getLocationRegistry().resolve(PROVIDER);
        machine = loc.obtain([userMetadata: [mykey: "myval"]]);
        
        Map<String,String> userMetadata = machine.getNode().getUserMetadata()
        assertEquals(userMetadata, [mykey: "myval"])
        assertTrue(machine.isSshable(), "machine="+machine)
    }
}
