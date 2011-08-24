package brooklyn.location.basic.jclouds

import static org.testng.Assert.*

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.DataProvider
import org.testng.annotations.Test

import brooklyn.location.basic.SshMachineLocation

public abstract class AbstractJcloudsLocationTest {
    protected static final Logger LOG = LoggerFactory.getLogger(AbstractJcloudsLocationTest.class)
    
    private final String provider
    private JcloudsLocationFactory locFactory;
    protected JcloudsLocation loc; // if private, can't be accessed from within closure in teardown! See http://jira.codehaus.org/browse/GROOVY-4692
    private Collection<SshMachineLocation> machines = []
    private File sshPrivateKey
    private File sshPublicKey

    protected AbstractJcloudsLocationTest(String provider) {
        this.provider = provider
    }
    
    /**
     * The location and image id tuplets to test.
     */
    @DataProvider(name = "fromImageId")
    public abstract Object[][] cloudAndImageIds();

    /**
     * The location and image name pattern tuplets to test.
     */
    @DataProvider(name = "fromImageNamePattern")
    public abstract Object[][] cloudAndImageNamePatterns();

    /**
     * The location, image pattern and image owner tuplets to test.
     */
    @DataProvider(name = "fromImagePattern")
    public abstract Object[][] cloudAndImagePatterns();

    @BeforeMethod(groups = "Live")
    public void setUp() {
        URL resource = getClass().getClassLoader().getResource("jclouds/id_rsa.private")
        assertNotNull resource
        sshPrivateKey = new File(resource.path)
        resource = getClass().getClassLoader().getResource("jclouds/id_rsa.pub")
        assertNotNull resource
        sshPublicKey = new File(resource.path)
        
        CredentialsFromEnv creds = new CredentialsFromEnv(provider);
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
    
    @Test(groups = [ "Live" ], dataProvider="fromImageId")
    public void testProvisionVmUsingImageId(String regionName, String imageId, String imageOwner) {
        loc = locFactory.newLocation(provider, regionName)
        loc.setTagMapping([MyEntityType:[
            imageId:imageId,
            imageOwner:imageOwner
        ]])
        
        Map flags = loc.getProvisioningFlags(["MyEntityType"])
        SshMachineLocation machine = obtainMachine(flags)
        
        LOG.info("Provisioned vm $machine; checking if ssh'able")
        assertTrue machine.isSshable()
    }
    
    @Test(groups = [ "Live" ], dataProvider="fromImageNamePattern")
    public void testProvisionVmUsingImageNamePattern(String regionName, String imageNamePattern, String imageOwner) {
        loc = locFactory.newLocation(provider, regionName)
        loc.setTagMapping([MyEntityType:[
            imageNamePattern:imageNamePattern,
            imageOwner:imageOwner
        ]])
        
        Map flags = loc.getProvisioningFlags(["MyEntityType"])
        SshMachineLocation machine = obtainMachine(flags)
        
        LOG.info("Provisioned AWS vm $machine; checking if ssh'able")
        assertTrue machine.isSshable()
    }
    
    @Test(groups = "Live", dataProvider="fromImagePattern")
    public void testProvisionVmUsingImagePattern(String regionName, String imagePattern, String imageOwner) {
        loc = locFactory.newLocation(provider, regionName)
        loc.setTagMapping([MyEntityType:[
            imagePattern:imagePattern,
            imageOwner:imageOwner
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
