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
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.Application
import brooklyn.entity.basic.Attributes
import brooklyn.entity.basic.legacy.JavaApp;
import brooklyn.entity.trait.Startable
import brooklyn.location.Location
import brooklyn.location.basic.LocalhostMachineProvisioningLocation
import brooklyn.test.entity.TestApplication
import brooklyn.util.internal.EntityStartUtils
import brooklyn.util.internal.TimeExtras

/**
 * Test the operation of the {@link QpidBroker} class.
 *
 * TODO clarify test purpose
 */
public class QpidIntegrationTest {
    private static final Logger log = LoggerFactory.getLogger(QpidIntegrationTest.class)

    static { TimeExtras.init() }

    private TestApplication app
    private Location testLocation
    private QpidBroker qpid

    @BeforeMethod(groups = "Integration")
    public void setup() {
        app = new TestApplication();
        testLocation = new LocalhostMachineProvisioningLocation(name:'london', count:2)
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
            assertTrue qpid.getAttribute(JavaApp.SERVICE_UP)
        }
        assertFalse qpid.getAttribute(JavaApp.SERVICE_UP)
    }

    /**
     * Test that the broker starts up and sets SERVICE_UP correctly.
     */
    @Test(groups = "Integration")
    public void canStartupAndShutdownWithPlugin() {
        Map qpidRuntimeFiles = [ ('lib/plugins/monterey-plugin.jar'):new File('src/test/resources/qpid-plugin.jar'),
                                 ('etc/config.xml'):new File('src/test/resources/qpid-config.xml') ]
        qpid = new QpidBroker(owner:app, runtimeFiles:qpidRuntimeFiles);
        qpid.start([ testLocation ])
        executeUntilSucceedsWithShutdown(qpid) {
            assertTrue qpid.getAttribute(JavaApp.SERVICE_UP)
        }
        assertFalse qpid.getAttribute(JavaApp.SERVICE_UP)
    }

    /**
     * Test that setting the 'queue' property causes a named queue to be created.
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
            assertTrue qpid.getAttribute(JavaApp.SERVICE_UP)
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
			Thread.sleep 1000
            assertEquals queue.getAttribute(QpidQueue.QUEUE_DEPTH_MESSAGES), 0
            sendMessages(connection, number, queue.queueName, content)

            // Check messages arrived
			Thread.sleep 1000
            assertEquals queue.getAttribute(QpidQueue.QUEUE_DEPTH_MESSAGES), number
            assertEquals queue.getAttribute(QpidQueue.QUEUE_DEPTH_BYTES), number * content.length()

            // Clear the messages
            assertEquals clearQueue(connection, queue.queueName), number

            // Check messages cleared
			Thread.sleep 1000
            assertEquals queue.getAttribute(QpidQueue.QUEUE_DEPTH_MESSAGES), 0
            assertEquals queue.getAttribute(QpidQueue.QUEUE_DEPTH_BYTES), 0
	        connection.close()

            // Close the JMS connection
        } finally {
            // Stop broker
	        qpid.stop()
        }
    }

    private Connection getQpidConnection(QpidBroker qpid) {
        int port = qpid.getAttribute(Attributes.AMQP_PORT)
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
