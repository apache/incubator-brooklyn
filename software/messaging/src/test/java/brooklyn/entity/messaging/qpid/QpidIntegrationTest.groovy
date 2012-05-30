package brooklyn.entity.messaging.qpid;

import static brooklyn.test.TestUtils.*
import static java.util.concurrent.TimeUnit.*
import static org.testng.Assert.*

import javax.jms.Connection
import javax.jms.MessageConsumer
import javax.jms.MessageProducer
import javax.jms.Queue
import javax.jms.Session
import javax.jms.TextMessage

import org.apache.qpid.client.AMQConnectionFactory
import org.apache.qpid.configuration.ClientProperties
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.basic.Attributes
import brooklyn.entity.trait.Startable
import brooklyn.location.Location
import brooklyn.location.basic.LocalhostMachineProvisioningLocation
import brooklyn.test.entity.TestApplication
import brooklyn.util.internal.TimeExtras

/**
 * Test the operation of the {@link QpidBroker} class.
 */
public class QpidIntegrationTest {
    private static final Logger log = LoggerFactory.getLogger(QpidIntegrationTest.class)

    static { TimeExtras.init() }

    private TestApplication app
    private Location testLocation
    private QpidBroker qpid

    @BeforeMethod(groups = "Integration")
    public void setup() {
        String workingDir = System.getProperty("user.dir");
        println workingDir
        app = new TestApplication();
        testLocation = new LocalhostMachineProvisioningLocation()
    }

    @AfterMethod(alwaysRun=true)
    public void shutdown() {
        if (app) app.stop()
    }

    /**
     * Test that the broker starts up and sets SERVICE_UP correctly.
     */
    @Test(groups = "Integration")
    public void canStartupAndShutdown() {
        qpid = new QpidBroker(owner:app);
        qpid.start([ testLocation ])
        executeUntilSucceedsWithShutdown(qpid) {
            assertTrue qpid.getAttribute(Startable.SERVICE_UP)
        }
        assertFalse qpid.getAttribute(Startable.SERVICE_UP)
    }

    /**
     * Test that the broker starts up and sets SERVICE_UP correctly when plugins are configured.
     */
    @Test(groups = "Integration")
    public void canStartupAndShutdownWithPlugin() {
        Map qpidRuntimeFiles;
        String pluginjar = "src/test/resources/qpid-test-plugin.jar";
        String configfile = "src/test/resources/qpid-test-config.xml";
        if (new File(pluginjar).isFile()) {
           qpidRuntimeFiles = [
                   ('lib/plugins/sample-plugin.jar'):new File(pluginjar),
                    ('etc/config.xml'):new File(configfile) ]
        } else {
           qpidRuntimeFiles = [
                   ('lib/plugins/sample-plugin.jar'):new File('software/messaging/'+pluginjar),
                   ('etc/config.xml'):new File('software/messaging/'+configfile) ]
        }
        qpid = new QpidBroker(owner:app, runtimeFiles:qpidRuntimeFiles);
        qpid.start([ testLocation ])
        //TODO assert the files/plugins were installed?
        executeUntilSucceedsWithShutdown(qpid) {
            assertTrue qpid.getAttribute(Startable.SERVICE_UP)
        }
        assertFalse qpid.getAttribute(Startable.SERVICE_UP)
    }

    /**
     * Test that setting the 'queue' property causes a named queue to be created.
     *
     * This test is disabled, pending further investigation. Issue with AMQP 0-10 queue names.
     */
    @Test(groups = "Integration")
    public void testCreatingQueues() {
        String queueName = "testQueue"
        int number = 20
        String content = "01234567890123456789012345678901"

        // Start broker with a configured queue
        qpid = new QpidBroker(owner:app, queue:queueName);
        qpid.start([ testLocation ])
        executeUntilSucceeds {
            assertTrue qpid.getAttribute(Startable.SERVICE_UP)
        }

        try {
            // Check queue created
            assertFalse qpid.queueNames.isEmpty()
            assertEquals qpid.queueNames.size(), 1
            assertTrue qpid.queueNames.contains(queueName)
            assertEquals qpid.ownedChildren.size(), 1
            assertFalse qpid.queues.isEmpty()
            assertEquals qpid.queues.size(), 1

            // Get the named queue entity
            QpidQueue queue = qpid.queues[queueName]
            assertNotNull queue

            // Connect to broker using JMS and send messages
            Connection connection = getQpidConnection(qpid)
            clearQueue(connection, queue.queueName)
            executeUntilSucceeds { assertEquals queue.getAttribute(QpidQueue.QUEUE_DEPTH_MESSAGES), 0 }
            sendMessages(connection, number, queue.queueName, content)

            // Check messages arrived
            executeUntilSucceeds { 
                assertEquals queue.getAttribute(QpidQueue.QUEUE_DEPTH_MESSAGES), number
                assertEquals queue.getAttribute(QpidQueue.QUEUE_DEPTH_BYTES), number * content.length()
            }

            //TODO clearing the queue currently returns 0
//            // Clear the messages -- should get 20
//            assertEquals clearQueue(connection, queue.queueName), 20
//
//            // Check messages cleared
//            executeUntilSucceeds {
//                assertEquals queue.getAttribute(QpidQueue.QUEUE_DEPTH_MESSAGES), 0
//                assertEquals queue.getAttribute(QpidQueue.QUEUE_DEPTH_BYTES), 0
//            }
            
	        connection.close()

            // Close the JMS connection
        } finally {
            // Stop broker
	        qpid.stop()
            qpid = null;
            app = null;
        }
    }

    private Connection getQpidConnection(QpidBroker qpid) {
        int port = qpid.getAttribute(Attributes.AMQP_PORT)
        System.setProperty(ClientProperties.AMQP_VERSION, "0-10");
        System.setProperty(ClientProperties.DEST_SYNTAX, "ADDR");
        AMQConnectionFactory factory = new AMQConnectionFactory("amqp://admin:admin@brooklyn/localhost?brokerlist='tcp://localhost:${port}'")
        Connection connection = factory.createConnection();
        connection.start();
        return connection
    }

    private void sendMessages(Connection connection, int count, String queueName, String content="") {
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)
        Queue destination = session.createQueue(queueName)
        MessageProducer messageProducer = session.createProducer(destination)

        count.times {
            TextMessage message = session.createTextMessage(content)
            messageProducer.send(message);
        }

        session.close()
    }

    private int clearQueue(Connection connection, String queueName) {
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)
        Queue destination = session.createQueue(queueName)
        MessageConsumer messageConsumer = session.createConsumer(destination)

        int received = 0
        while (messageConsumer.receive(500) != null) received++

        session.close()
        
        received
    }
}
