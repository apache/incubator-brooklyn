package brooklyn.location.basic.aws

import static org.testng.Assert.*

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.location.basic.SshMachineLocation

class AwsLocationTest {
    private static final Logger LOG = LoggerFactory.getLogger(AwsLocationTest.class)
    
    private static final String EUWEST_REGION_NAME = "eu-west-1" 
    private static final String USEAST_REGION_NAME = "us-east-1" 
    private static final String EUWEST_IMAGE_ID = EUWEST_REGION_NAME+"/"+"ami-89def4fd"
    private static final String USEAST_IMAGE_ID = USEAST_REGION_NAME+"/"+"ami-2342a94a"
    private static final String IMAGE_OWNER = "411009282317"
    private static final String IMAGE_PATTERN = ".*RightImage_CentOS_5.4_i386_v5.5.9_EBS.*"
    
    private AwsLocationFactory locFactory;
    private AwsLocation loc;
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
        
        CredentialsFromEnv creds = new CredentialsFromEnv();
        locFactory = new AwsLocationFactory([
                identity:creds.getAWSAccessKeyId(), 
                credential:creds.getAWSSecretKey(), 
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
    }
    
    @Test(groups = ["Live", "WIP"] )
    public void testProvisionVmInEuWestUsingImageId() {
        loc = locFactory.newLocation(EUWEST_REGION_NAME)
        loc.setTagMapping([MyEntityType:[
            imageId:EUWEST_IMAGE_ID,
        ]]) //, imageOwner:IMAGE_OWNER]])
        
        Map flags = loc.getProvisioningFlags(["MyEntityType"])
        SshMachineLocation machine = obtainMachine(flags)
        
        assertTrue machine.isSshable()
    }
    
    @Test(groups = ["Live", "WIP"] )
    public void testProvisionVmInUsEastUsingImageId() {
        loc = locFactory.newLocation(USEAST_REGION_NAME)
        loc.setTagMapping([MyEntityType:[
            imageId:USEAST_IMAGE_ID,
        ]]) //, imageOwner:IMAGE_OWNER]])
        
        Map flags = loc.getProvisioningFlags(["MyEntityType"])
        SshMachineLocation machine = obtainMachine(flags)
        
        assertTrue machine.isSshable()
    }
    
    @Test(groups = ["Live", "WIP"] )
    public void testProvisionVmInEuWestUsingImagePattern() {
        loc = locFactory.newLocation(EUWEST_REGION_NAME)
        loc.setTagMapping([MyEntityType:[
            imagePattern:IMAGE_PATTERN,
            imageOwner:IMAGE_OWNER
        ]])
        
        Map flags = loc.getProvisioningFlags(["MyEntityType"])
        SshMachineLocation machine = obtainMachine(flags)
        
        assertTrue machine.isSshable()
    }
    
    @Test(groups = ["Live", "WIP"] )
    public void testProvisionVmInUsEastUsingImagePattern() {
        loc = locFactory.newLocation(USEAST_REGION_NAME)
        loc.setTagMapping([MyEntityType:[
            imagePattern:IMAGE_PATTERN,
            imageOwner:IMAGE_OWNER
        ]])
        
        Map flags = loc.getProvisioningFlags(["MyEntityType"])
        SshMachineLocation machine = obtainMachine(flags)
        
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
