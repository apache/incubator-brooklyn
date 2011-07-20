package brooklyn.location.basic.aws

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.Assert
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.location.basic.SshMachineLocation

class AwsLocationTest {
    private static final Logger LOG = LoggerFactory.getLogger(AwsLocationTest.class)
    
    private static final String REGION_NAME = "us-east-1" // "eu-west-1"
    private static final String IMAGE_ID = REGION_NAME+"/"+"ami-0859bb61" // "ami-d7bb90a3"
    private static final String IMAGE_OWNER = "411009282317"
    
    private AwsLocation loc;
    private Collection<SshMachineLocation> machines = []
    
    @BeforeMethod(groups = "Live")
    public void setUp() {
        AWSCredentialsFromEnv creds = new AWSCredentialsFromEnv();
        loc = new AwsLocation(identity:creds.getAWSAccessKeyId(), credential:creds.getAWSSecretKey(), providerLocationId:REGION_NAME)
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
    public void testProvisionVm() {
        loc.setTagMapping([MyEntityType:[
            imageId:IMAGE_ID,
            providerLocationId:REGION_NAME,
            sshPublicKey:new File("/Users/adk/.ssh/id_rsa.pub"),
            sshPrivateKey:new File("/Users/adk/.ssh/id_rsa"),
        ]]) //, imageOwner:IMAGE_OWNER]])
        
        Map flags = loc.getProvisioningFlags(["MyEntityType"])
        SshMachineLocation machine = obtainMachine(flags)
        
        Assert.assertTrue machine.isSshable()
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
