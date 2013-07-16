package brooklyn.location.jclouds

import static org.testng.Assert.*

import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.basic.Entities
import brooklyn.location.jclouds.JcloudsLocation;
import brooklyn.location.jclouds.JcloudsSshMachineLocation;
import brooklyn.management.ManagementContext

import com.google.common.collect.ImmutableMap

/** asserts successful creation of a VM based on an image with the name "linux-no-firewall"
 *  (you must create the image manually first) */
public class RackspaceUkLocationWithImageNameLiveTest {
    
    private static final String PROVIDER = "rackspace-cloudservers-uk"

    private ManagementContext managementContext;
    JcloudsLocation loc
    JcloudsSshMachineLocation machine
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() {
        managementContext = Entities.newManagementContext(
            ImmutableMap.builder()
            .put("provider", PROVIDER)
            .put("brooklyn.jclouds.rackspace-cloudservers-uk.imageNameRegex", ".*Ubuntu 11.10.*")
            .put("brooklyn.jclouds.rackspace-cloudservers-uk.hardwareId", "1")  //gives the 80gb disk needed
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
     *   <li>This requires a dependency on the mvn org.jclouds.provider:rackspace-cloudservers-uk module.
     *       This is included by default from the org.jclouds:jclouds-allcompute dependency.
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
