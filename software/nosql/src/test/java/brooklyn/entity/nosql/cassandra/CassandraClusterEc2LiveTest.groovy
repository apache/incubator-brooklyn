package brooklyn.entity.nosql.cassandra

import static brooklyn.test.TestUtils.executeUntilSucceeds
import static brooklyn.test.TestUtils.executeUntilSucceedsWithShutdown
import static org.testng.Assert.*

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.trait.Startable
import brooklyn.location.basic.SshMachineLocation
import brooklyn.location.basic.jclouds.CredentialsFromEnv
import brooklyn.location.basic.jclouds.JcloudsLocation
import brooklyn.location.basic.jclouds.JcloudsLocationFactory
import brooklyn.test.entity.TestApplication
import brooklyn.util.internal.TimeExtras

class CassandraClusterEc2LiveTest {
    protected static final Logger LOG = LoggerFactory.getLogger(CassandraClusterEc2LiveTest.class)

    static { TimeExtras.init() }

    private final String provider
    protected JcloudsLocation loc;
    protected JcloudsLocationFactory locFactory;
    private Collection<SshMachineLocation> machines = []
    private File sshPrivateKey
    private File sshPublicKey

    TestApplication app
    CassandraCluster cluster

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

        String regionName = "eu-west-1"
        String imageId = "eu-west-1/ami-89def4fd"
        String imageOwner = "411009282317"

        loc = locFactory.newLocation(regionName)
        loc.setTagMapping([(CassandraServer.class.getName()):[
            imageId:imageId,
            imageOwner:imageOwner,
            securityGroups:["brooklyn-all"]
        ]])

        app = new TestApplication()
    }

    @AfterMethod(groups = "Live")
    public void tearDown() {
        try {
            if (app) app.stop()
        } finally {
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
    }

    /**
     * Test that the server starts up and sets SERVICE_UP correctly.
     */
    @Test(groups = [ "Live" ])
    public void canStartupAndShutdown() {
        cluster = new CassandraCluster(owner:app, initialSize:2, clusterName:'Amazon Cluster');
        app.start([ loc ])
        executeUntilSucceeds(cluster) {
            assertTrue cluster.currentSize == 2
            cluster.members.each {
                assertTrue it.getAttribute(Startable.SERVICE_UP)
            }
        }
        // cluster.stop()
        // assertFalse cluster.getAttribute(Startable.SERVICE_UP)
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
