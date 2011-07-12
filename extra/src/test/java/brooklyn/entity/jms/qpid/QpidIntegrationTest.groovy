package brooklyn.entity.jms.qpid;

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
import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.basic.Attributes
import brooklyn.entity.basic.JavaApp
import brooklyn.location.Location
import brooklyn.location.basic.LocalhostMachineProvisioningLocation
import brooklyn.util.internal.TimeExtras

/**
 * Test the operation of the {@link QpidBroker} class.
 *
 * TODO clarify test purpose
 */
public class QpidBrokerIntegrationTest {
    private static final Logger log = LoggerFactory.getLogger(brooklyn.entity.webapp.jboss.JBossNodeIntegrationTest)

    static { TimeExtras.init() }

    private Application app
    private Location testLocation
    private QpidBroker qpid

    static class TestApplication extends AbstractApplication {
        public TestApplication(Map properties=[:]) {
            super(properties)
        }
    }

    @BeforeMethod(groups = "Integration")
    public void setup() {
        app = new TestApplication();
        testLocation = new LocalhostMachineProvisioningLocation(name:'london', count:2)
    }

    @AfterMethod(groups = "Integration")
    public void shutdown() {
        if (qpid.getAttribute(JavaApp.NODE_UP)) {
            log.warn "Qpid broker {} is still running", qpid.id
	        try {
	            qpid.stop()
	        } catch (Exception e) {
	            log.warn "Error caught trying to shut down Qpid: {}", e.message
	        }
        }
    }

    /**
     * Test that the broker starts up and sets NODE_UP correctly.
     */
    @Test(groups = "Integration")
    public void canStartupAndShutdown() {
        qpid = new QpidBroker(owner:app);
        qpid.start([ testLocation ])
        executeUntilSucceedsWithFinallyBlock ([:], {
            assertTrue qpid.getAttribute(JavaApp.NODE_UP)
        }, {
            qpid.stop()
        })
        assertFalse qpid.getAttribute(JavaApp.NODE_UP)
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
        executeUntilSucceeds([:], {
            assertTrue qpid.getAttribute(JavaApp.NODE_UP)
        })

        try {
            // Check queue created
            assertFalse qpid.queueNames.isEmpty()
            assertEquals qpid.queueNames.size(), 1
            assertTrue qpid.queueNames.contains(queueName)
            assertEquals qpid.ownedChildren.size(), 1
            assertFalse qpid.queues.isEmpty()
            assertEquals qpid.queues.size(), 1
            assertNotNull qpid.queues[queueName]

            // Connect to broker using JMS and send messages
            Connection connection = getQpidConnection(qpid)
            clearQueue(connection, "BURL:${queueName}")
            sendMessages(connection, number, "BURL:${queueName}", content)
            Thread.sleep 1000

            // Check messages arrived
            assertEquals qpid.queues[queueName].getAttribute(QpidQueue.MESSAGE_COUNT), number
            assertEquals qpid.queues[queueName].getAttribute(QpidQueue.QUEUE_DEPTH), number * content.length()

            // Clear the messages
            assertEquals clearQueue(connection, "BURL:${queueName}"), number
            Thread.sleep 1000

            // Check messages cleared
            assertEquals qpid.queues[queueName].getAttribute(QpidQueue.MESSAGE_COUNT), 0
            assertEquals qpid.queues[queueName].getAttribute(QpidQueue.QUEUE_DEPTH), 0
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
