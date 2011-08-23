package brooklyn.location.basic.jclouds

import static org.testng.Assert.*

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.location.basic.SshMachineLocation

class GoGridLocationTest {
    private static final Logger LOG = LoggerFactory.getLogger(GoGridLocationTest.class)
    
    private static final String PROVIDER = "gogrid"
    private static final String USWEST_REGION_NAME = "1"//"us-west-1"
    private static final String USWEST_IMAGE_ID = "1532"
    private static final String IMAGE_NAME_PATTERN = "CentOS 5.3 (64-bit) w/ None"
    
    private JcloudsLocationFactory locFactory;
    private JcloudsLocation loc;
    private Collection<SshMachineLocation> machines = []
    private File sshPrivateKey
    private File sshPublicKey
    
    @BeforeMethod(groups = "Live")
    public void setUp() {
        URL resource = getClass().getClassLoader().getResource("jclouds/id_rsa.private")
        assertNotNull resource
        sshPrivateKey = new File(resource.path)
        resource = getClass().getClassLoader().getResource("jclouds/id_rsa.pub")
        assertNotNull resource
        sshPublicKey = new File(resource.path)
        
        CredentialsFromEnv creds = new CredentialsFromEnv(PROVIDER);
        locFactory = new JcloudsLocationFactory([
                identity:creds.getIdentity(), 
                credential:creds.getCredential(), 
                sshPublicKey:sshPublicKey,
                sshPrivateKey:sshPrivateKey])
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
    
    @Test(groups = "Live")
    public void testProvisionVmInGoGridUsWestUsingImageId() {
        loc = locFactory.newLocation(PROVIDER, USWEST_REGION_NAME)
        loc.setTagMapping([MyEntityType:[
            imageId:USWEST_IMAGE_ID,
        ]])
        
        Map flags = loc.getProvisioningFlags(["MyEntityType"])
        SshMachineLocation machine = obtainMachine(flags)
        
        LOG.info("Provisioned AWS vm $machine; checking if ssh'able")
        assertTrue machine.isSshable()
    }
    
    @Test(groups = "Live")
    public void testProvisionVmInUsWestUsingImagePattern() {
        loc = locFactory.newLocation(PROVIDER, USWEST_REGION_NAME)
        loc.setTagMapping([MyEntityType:[
            imageNamePattern:IMAGE_NAME_PATTERN
        ]])
        
        Map flags = loc.getProvisioningFlags(["MyEntityType"])
        SshMachineLocation machine = obtainMachine(flags)
        
        LOG.info("Provisioned AWS vm $machine; checking if ssh'able")
        assertTrue machine.isSshable()
    }
    
    // Use this utility method to ensure 
    private SshMachineLocation obtainMachine(Map flags) {
        SshMachineLocation result = loc.obtain(flags)
        machines.add(result)
        return result
    }
    
    private SshMachineLocation release(SshMachineLocation machine) {
        machines.remove(machine)
        loc.release(machine)
    }
}
