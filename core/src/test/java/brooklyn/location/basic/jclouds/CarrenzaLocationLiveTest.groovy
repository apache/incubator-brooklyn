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

/**
 * Tests vcloud, with Carrenza. Uses the cloudsoft test account (hard-coding its NAT Mapping, 
 * and one of its private vApp templates). Note that the template is for a Windows 2008 
 * machine with winsshd installed.
 * 
 * TODO Will only work with jclouds 1.5, due to jclouds issues 994 and 995. Therefore it 
 * will not work in brooklyn 0.4.0-M2 etc.
 */
class CarrenzaLocationLiveTest {
    protected static final Logger LOG = LoggerFactory.getLogger(CarrenzaLocationLiveTest.class)
    
    private static final String PROVIDER = "vcloud"
    private static final String ENDPOINT = "https://myvdc.carrenza.net/api"
    private static final String LOCATION_ID = "jclouds:"+PROVIDER+":"+ENDPOINT;
    private static final String WINDOWS_IMAGE_ID = "https://myvdc.carrenza.net/api/v1.0/vAppTemplate/vappTemplate-2bd5b0ff-ecd9-405e-8306-2f4f6c092a1b"
    
    private JcloudsLocation loc;
    private Collection<SshMachineLocation> machines = []
    
    @BeforeMethod(groups = "Live")
    public void setUp() {
        System.out.println("classpath="+System.getProperty("java.class.path"));
        
        CredentialsFromEnv creds = getCredentials();
        
        List<Location> locations = new LocationRegistry(
                ImmutableMap.builder()
                        .put("identity", creds.getIdentity())
                        .put("credential", creds.getCredential())
                        .put("jclouds.endpoint", ENDPOINT)
                        .put("imageId", WINDOWS_IMAGE_ID)
                        .put("noDefaultSshKeys", true)
                        .put("userName", "Administrator")
                        .put("dontCreateUser", true)
                        .put("overrideLoginUser", "Administrator")
                        .put("waitForSshable", false)
                        .put("runAsRoot", false)
                        .put("inboundPorts", [22, 3389])
                        .put("natMapping", [("192.168.0.100"):"195.3.186.200", ("192.168.0.101"):"195.3.186.42"])
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
    
    // FIXME Disabled because of jclouds issues #994 and #995 (fixed in jclouds 1.5, so not in brooklyn 0.4.0-M2 etc)
    // Note the careful settings in setUp (e.g. so don't try to install ssh-keys etc
    // Also, the windows image used has winsshd installed
    @Test(enabled=false, groups = [ "Live" ])
    public void testProvisionWindowsVm() {
        JcloudsSshMachineLocation machine = obtainMachine(MutableMap.of(
                "imageId", WINDOWS_IMAGE_ID));
        
        LOG.info("Provisioned Windows VM {}; checking if has password", machine)
        String password = machine.waitForPassword();
        assertNotNull(password);
        
        LOG.info("Checking can ssh to windows machine {} using password {}", machine, password);
        assertEquals(machine.exec(MutableMap.of("password", password), ImmutableList.of("hostname")), 0);
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
