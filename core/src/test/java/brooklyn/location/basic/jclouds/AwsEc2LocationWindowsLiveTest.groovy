package brooklyn.location.basic.jclouds;

import static org.testng.Assert.*

import org.slf4j.Logger;
import org.slf4j.LoggerFactory
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.common.collect.Iterables

import brooklyn.location.Location
import brooklyn.location.basic.LocationRegistry;
import brooklyn.location.basic.SshMachineLocation
import brooklyn.location.basic.jclouds.JcloudsLocation.JcloudsSshMachineLocation
import brooklyn.util.MutableMap

class AwsEc2LocationWindowsLiveTest {
    protected static final Logger LOG = LoggerFactory.getLogger(AwsEc2LocationWindowsLiveTest.class)
    
    private static final String PROVIDER = "aws-ec2"
    private static final String EUWEST_REGION_NAME = "eu-west-1" 
    private static final String EUWEST_IMAGE_ID = EUWEST_REGION_NAME+"/"+"ami-7f0c260b";//"ami-41d3d635"
    private static final String LOCATION_ID = "jclouds:"+PROVIDER+":"+EUWEST_REGION_NAME;
    
    private JcloudsLocation loc;
    private Collection<SshMachineLocation> machines = []
    
    @BeforeMethod(groups = "Live")
    public void setUp() {
        CredentialsFromEnv creds = getCredentials();
        
        List<Location> locations = new LocationRegistry(
                ImmutableMap.builder()
                        .put("identity", creds.getIdentity())
                        .put("credential", creds.getCredential())
                        .put("imageId", EUWEST_IMAGE_ID)
                        .put("hardwareId", "t1.micro")
                        .put("noDefaultSshKeys", true)
                        .put("userName", "Administrator")
                        .put("dontCreateUser", true)
                        .put("overrideLoginUser", "Administrator")
                        .put("waitForSshable", false)
                        .put("runAsRoot", false)
                        .put("inboundPorts", [22, 3389])
                        .build())
                .getLocationsById(ImmutableList.of(LOCATION_ID));

        loc = (JcloudsLocation) Iterables.get(locations, 0);
    }
    
    protected CredentialsFromEnv getCredentials() {
        return new CredentialsFromEnv(PROVIDER);
    }
    
    @AfterMethod(groups = "Live")
    public void tearDown() {
        List<Exception> exceptions = []
        machines.each {
            try {
                loc?.release(it)
            } catch (Exception e) {
                LOG.warn("Error releasing machine $it; continuing...", e)
                exceptions.add(e)
            }
        }
        if (exceptions) {
            throw exceptions.get(0)
        }
        machines.clear()
    }
    
    // TODO Note careful choice of image due to jclouds 1.4 issue 886
    // TODO Blocks for long time, waiting for IP:22 to be reachable, before falling back to using public IP
    //      10*2 minutes per attempt in jclouds 1.4 because done sequentially, and done twice by us so test takes 40 minutes!
    @Test(enabled=true, groups = [ "Live" ])
    public void testProvisionWindowsVm() {
        JcloudsSshMachineLocation machine = obtainMachine(MutableMap.of());
        
        LOG.info("Provisioned Windows VM {}; checking if has password", machine)
        assertNotNull(machine.waitForPassword())
    }
    
    // Use this utility method to ensure machines are released on tearDown
    protected SshMachineLocation obtainMachine(Map flags) {
        SshMachineLocation result = loc.obtain(flags)
        machines.add(result)
        return result
    }
    
    protected SshMachineLocation release(SshMachineLocation machine) {
        machines.remove(machine)
        loc.release(machine)
    }
}
