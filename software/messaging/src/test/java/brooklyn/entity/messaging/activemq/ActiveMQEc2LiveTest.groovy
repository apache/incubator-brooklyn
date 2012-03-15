package brooklyn.entity.messaging.activemq

import static org.testng.Assert.*
import static brooklyn.test.TestUtils.executeUntilSucceeds
import static brooklyn.test.TestUtils.executeUntilSucceedsWithShutdown

import brooklyn.entity.basic.legacy.JavaApp;
import brooklyn.entity.trait.Startable
import brooklyn.location.basic.SshMachineLocation
import brooklyn.location.basic.jclouds.CredentialsFromEnv
import brooklyn.location.basic.jclouds.JcloudsLocation
import brooklyn.location.basic.jclouds.JcloudsLocationFactory
import brooklyn.test.entity.TestApplication
import brooklyn.util.internal.EntityStartUtils
import brooklyn.util.internal.TimeExtras
import org.apache.activemq.ActiveMQConnectionFactory
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test
import javax.jms.Connection
import javax.jms.MessageConsumer
import javax.jms.Session
import javax.jms.TextMessage
import javax.jms.MessageProducer

/**
 * Created by IntelliJ IDEA.
 * User: richard
 * Date: 29/09/2011
 * Time: 23:34
 * To change this template use File | Settings | File Templates.
 */
class ActiveMQEc2LiveTest {
    protected static final Logger LOG = LoggerFactory.getLogger(ActiveMQEc2LiveTest.class)

    static { TimeExtras.init() }

    private final String provider
    protected JcloudsLocation loc;
    protected JcloudsLocationFactory locFactory;
    private Collection<SshMachineLocation> machines = []
    private File sshPrivateKey
    private File sshPublicKey
    TestApplication app
    ActiveMQBroker activeMQ

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
        loc.setTagMapping([(ActiveMQBroker.class.getName()):[
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
     * Test that the broker starts up and sets SERVICE_UP correctly.
     */
    @Test(groups = [ "Live" ])
    public void canStartupAndShutdown() {
        activeMQ = new ActiveMQBroker(owner:app);
        app.start([ loc ])
        executeUntilSucceedsWithShutdown(activeMQ) {
            assertTrue activeMQ.getAttribute(JavaApp.SERVICE_UP)
        }
        assertFalse activeMQ.getAttribute(JavaApp.SERVICE_UP)
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

    /**
     * Test that setting the 'queue' property causes a named queue to be created.
     */
    @Test(groups = "Live")
    public void testCreatingQueues() {
        String queueName = "testQueue"
        int number = 20
        String content = "01234567890123456789012345678901"

        // Start broker with a configured queue
        activeMQ = new ActiveMQBroker(owner:app, queue:queueName);
        app.start([ loc ])
        executeUntilSucceeds {
            assertTrue activeMQ.getAttribute(JavaApp.SERVICE_UP)
        }

        try {
            // Check queue created
            assertFalse activeMQ.queueNames.isEmpty()
            assertEquals activeMQ.queueNames.size(), 1
            assertTrue activeMQ.queueNames.contains(queueName)
            assertEquals activeMQ.ownedChildren.size(), 1
            assertFalse activeMQ.queues.isEmpty()
            assertEquals activeMQ.queues.size(), 1

            // Get the named queue entity
            ActiveMQQueue queue = activeMQ.queues[queueName]
            assertNotNull queue
            assertEquals queue.name, queueName

            // Connect to broker using JMS and send messages
            Connection connection = getActiveMQConnection(activeMQ)
            clearQueue(connection, queueName)
			Thread.sleep 1000
            assertEquals queue.getAttribute(ActiveMQQueue.QUEUE_DEPTH_MESSAGES), 0
            sendMessages(connection, number, queueName, content)

            // Check messages arrived
			Thread.sleep 1000
            assertEquals queue.getAttribute(ActiveMQQueue.QUEUE_DEPTH_MESSAGES), number

            // Clear the messages
            assertEquals clearQueue(connection, queueName), number

            // Check messages cleared
			Thread.sleep 1000
            assertEquals queue.getAttribute(ActiveMQQueue.QUEUE_DEPTH_MESSAGES), 0
	        connection.close()

            // Close the JMS connection
        } finally {
            // Stop broker
	        activeMQ.stop()
        }
    }

    private Connection getActiveMQConnection(ActiveMQBroker activeMQ) {
        int port = activeMQ.getAttribute(ActiveMQBroker.OPEN_WIRE_PORT)
        String address = activeMQ.getAttribute(ActiveMQBroker.ADDRESS)
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory("tcp://${address}:${port}".toString())
        Connection connection = factory.createConnection("admin", "activemq");
        connection.start();
        return connection
    }

    private void sendMessages(Connection connection, int count, String queueName, String content="") {
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)
        org.apache.activemq.command.ActiveMQQueue destination = session.createQueue(queueName)
        MessageProducer messageProducer = session.createProducer(destination)

        count.times {
            TextMessage message = session.createTextMessage(content)
            messageProducer.send(message);
        }

        session.close()
    }

    private int clearQueue(Connection connection, String queueName) {
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)
        org.apache.activemq.command.ActiveMQQueue destination = session.createQueue(queueName)
        MessageConsumer messageConsumer = session.createConsumer(destination)

        int received = 0
        while (messageConsumer.receive(500) != null) received++

        session.close()

        received
    }
}
