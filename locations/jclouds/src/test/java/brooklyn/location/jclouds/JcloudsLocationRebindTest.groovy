package brooklyn.location.jclouds

import static org.testng.Assert.*

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.basic.Entities
import brooklyn.location.basic.SshMachineLocation
import brooklyn.location.jclouds.CredentialsFromEnv;
import brooklyn.location.jclouds.JcloudsLocation;
import brooklyn.location.jclouds.JcloudsSshMachineLocation;
import brooklyn.management.ManagementContext

import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet

public class JcloudsLocationRebindTest {
    protected static final Logger LOG = LoggerFactory.getLogger(JcloudsLocationRebindTest.class)
    
    private static final String PROVIDER = "aws-ec2"
    private static final String EUWEST_REGION_NAME = "eu-west-1"
    private static final String EUWEST_IMAGE_ID = EUWEST_REGION_NAME+"/"+"ami-89def4fd"
    private static final String IMAGE_OWNER = "411009282317"

    protected ManagementContext managementContext;
    protected JcloudsLocation loc; // if private, can't be accessed from within closure in teardown! See http://jira.codehaus.org/browse/GROOVY-4692
    private Collection<SshMachineLocation> machines = []
//    private File sshPrivateKey
//    private File sshPublicKey

    protected CredentialsFromEnv getCredentials() {
        return new CredentialsFromEnv(PROVIDER);
    }
    
    @BeforeMethod(groups = "Live")
    public void setUp() {
//        URL resource = getClass().getClassLoader().getResource("jclouds/id_rsa.private")
//        assertNotNull resource
//        sshPrivateKey = new File(resource.path)
//        resource = getClass().getClassLoader().getResource("jclouds/id_rsa.pub")
//        assertNotNull resource
//        sshPublicKey = new File(resource.path)
        
        ManagementContext managementContext = Entities.newManagementContext(
            ImmutableMap.of("provider", PROVIDER));
//        CredentialsFromEnv creds = getCredentials();
//        locFactory = new JcloudsLocationFactory([
//                provider:PROVIDER,
//                identity:creds.getIdentity(), 
//                credential:creds.getCredential(), 
//                sshPublicKey:sshPublicKey,
//                sshPrivateKey:sshPrivateKey])
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
    
    @Test(groups = [ "Live" ])
    public void testRebindWithIncorrectId() {
        loc = managementContext.getLocationRegistry().resolve(provider+":"+EUWEST_REGION_NAME);
        try {
            loc.rebindMachine(id:"incorrectid", hostname:"myhostname", userName:"myusername")
        } catch (IllegalArgumentException e) {
            if (e.message.contains("Invalid id")) {
                // success
            } else {
                throw e
            }
        }
    }
    
    @Test(groups = [ "Live" ])
    public void testRebindVm() {
        // FIXME How to create a machine - go directly through jclouds instead?
        //       Going through LocationRegistry.resolve, loc and loc2 might be same instance
        
        loc = managementContext.getLocationRegistry().resolve(provider+":"+EUWEST_REGION_NAME);

        // Create a VM through jclouds
        JcloudsSshMachineLocation machine = obtainMachine([imageId:EUWEST_IMAGE_ID, imageOwner:IMAGE_OWNER])
        assertTrue(machine.isSshable())

        String id = machine.getJcloudsId()
        InetAddress address = machine.getAddress()
        String hostname = address.getHostName()
        String user = machine.getUser()
        
        // Create a new jclouds location, and re-bind the existing VM to that
        JcloudsLocation loc2 = managementContext.getLocationRegistry().resolve(provider+":"+EUWEST_REGION_NAME);
        SshMachineLocation machine2 = loc2.rebindMachine(id:id, hostname:hostname, user:user)
        
        // Confirm the re-bound machine is wired up
        assertTrue(machine2.isSshable())
        assertEquals(ImmutableSet.copyOf(loc2.getChildren()), ImmutableSet.of(machine2))
        
        // Confirm can release the re-bound machine via the new jclouds location
        loc2.release(machine2)
        assertFalse(machine2.isSshable())
        assertEquals(ImmutableSet.copyOf(loc2.getChildren()), Collections.emptySet())
    }
    
    @Test(groups = [ "Live" ])
    public void testRebindVmDeprecated() {
        // FIXME See comments in testRebindVm
        loc = managementContext.getLocationRegistry().resolve(provider+":"+EUWEST_REGION_NAME);

        // Create a VM through jclouds
        JcloudsSshMachineLocation machine = obtainMachine([imageId:EUWEST_IMAGE_ID, imageOwner:IMAGE_OWNER])
        assertTrue(machine.isSshable())

        String id = machine.getJcloudsId()
        InetAddress address = machine.getAddress()
        String hostname = address.getHostName()
        String username = machine.getUser()
        
        // Create a new jclouds location, and re-bind the existing VM to that
        JcloudsLocation loc2 = managementContext.getLocationRegistry().resolve(provider+":"+EUWEST_REGION_NAME);
        // pass deprecated userName
        SshMachineLocation machine2 = loc2.rebindMachine(id:id, hostname:hostname, userName:username)
        
        // Confirm the re-bound machine is wired up
        assertTrue(machine2.isSshable())
        assertEquals(ImmutableSet.copyOf(loc2.getChildren()), ImmutableSet.of(machine2))
        
        // Confirm can release the re-bound machine via the new jclouds location
        loc2.release(machine2)
        assertFalse(machine2.isSshable())
        assertEquals(ImmutableSet.copyOf(loc2.getChildren()), Collections.emptySet())
    }

    // Useful for debugging; accesss a hard-coded existing instance so don't need to wait for provisioning a new one
    @Test(enabled=false, groups = [ "Live" ])
    public void testRebindVmToHardcodedInstance() {
        loc = managementContext.getLocationRegistry().resolve(provider+":"+EUWEST_REGION_NAME);

        String id = "eu-west-1/i-5504f21d"
        InetAddress address = InetAddress.getByName("ec2-176-34-93-58.eu-west-1.compute.amazonaws.com")
        String hostname = address.getHostName()
        String username = "root"
        
        SshMachineLocation machine = loc.rebindMachine(id:id, hostname:hostname, userName:username)
        
        // Confirm the re-bound machine is wired up
        assertTrue(machine.isSshable())
        assertEquals(ImmutableSet.copyOf(loc.getChildren()), ImmutableSet.of(machine))
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
