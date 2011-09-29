package brooklyn.entity.messaging.activemq

import static org.testng.Assert.*
import brooklyn.location.basic.SshMachineLocation
import org.testng.annotations.Test
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.slf4j.LoggerFactory
import org.slf4j.Logger
import brooklyn.test.entity.TestApplication
import brooklyn.location.basic.jclouds.AbstractJcloudsLocationTest
import brooklyn.location.basic.jclouds.JcloudsLocationFactory
import brooklyn.location.basic.jclouds.JcloudsLocation
import brooklyn.location.basic.jclouds.CredentialsFromEnv
import brooklyn.entity.basic.AbstractService
import brooklyn.entity.trait.Startable

/**
 * Created by IntelliJ IDEA.
 * User: richard
 * Date: 29/09/2011
 * Time: 23:34
 * To change this template use File | Settings | File Templates.
 */
class ActiveMQEc2LiveTest {
    protected static final Logger LOG = LoggerFactory.getLogger(AbstractJcloudsLocationTest.class)

    private final String provider
    protected JcloudsLocationFactory locFactory;
    protected JcloudsLocation loc; // if private, can't be accessed from within closure in teardown! See http://jira.codehaus.org/browse/GROOVY-4692
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

        CredentialsFromEnv creds = new CredentialsFromEnv("aws-ec2");
        locFactory = new JcloudsLocationFactory([
                provider:"aws-ec2",
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

    @Test(groups = [ "Live" ])
    public void testProvisionVmUsingImageId() {
        String regionName = "eu-west-1"
        String imageId = "eu-west-1/ami-89def4fd"
        String imageOwner = "411009282317"

        loc = locFactory.newLocation(regionName)
        loc.setTagMapping([MyEntityType:[
            imageId:imageId,
            imageOwner:imageOwner,
            securityGroups:["brooklyn-all"]
        ]])

        Map flags = loc.getProvisioningFlags(["MyEntityType"])
        SshMachineLocation machine = obtainMachine(flags)

        LOG.info("Provisioned vm $machine; checking if ssh'able")
        assertTrue machine.isSshable()

        TestApplication app = new TestApplication()
        ActiveMQBroker amq = new ActiveMQBroker([:], app)
        app.start([machine])
        assertTrue amq[Startable.SERVICE_UP]
    }

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
