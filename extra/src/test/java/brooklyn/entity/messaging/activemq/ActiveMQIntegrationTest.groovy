package brooklyn.entity.messaging.activemq;

import static brooklyn.test.TestUtils.*
import static java.util.concurrent.TimeUnit.*
import static org.testng.Assert.*

import javax.jms.Connection
import javax.jms.MessageConsumer
import javax.jms.MessageProducer
import javax.jms.Queue
import javax.jms.Session
import javax.jms.TextMessage

import org.apache.activemq.ActiveMQConnectionFactory
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.Application
import brooklyn.entity.basic.JavaApp
import brooklyn.entity.trait.Startable
import brooklyn.location.Location
import brooklyn.location.basic.LocalhostMachineProvisioningLocation
import brooklyn.test.entity.TestApplication
import brooklyn.util.internal.EntityStartUtils
import brooklyn.util.internal.TimeExtras

/**
 * Test the operation of the {@link ActiveMQBroker} class.
 *
 * TODO clarify test purpose
 */
public class ActiveMQIntegrationTest {
    private static final Logger log = LoggerFactory.getLogger(ActiveMQIntegrationTest.class)

    static { TimeExtras.init() }

    private Application app
    private Location testLocation
    private ActiveMQBroker activeMQ

    @BeforeMethod(groups = "Integration")
    public void setup() {
        app = new TestApplication();
        testLocation = new LocalhostMachineProvisioningLocation(name:'london', count:2)
    }

    @AfterMethod(groups = "Integration")
    public void shutdown() {
        if (activeMQ != null && activeMQ.getAttribute(Startable.SERVICE_UP)) {
	        EntityStartUtils.stopEntity(activeMQ)
        }
    }

    /**
     * Test that the broker starts up and sets SERVICE_UP correctly.
     */
    @Test(groups = "Integration")
    public void canStartupAndShutdown() {
        activeMQ = new ActiveMQBroker(owner:app);
        activeMQ.start([ testLocation ])
        executeUntilSucceedsWithShutdown(activeMQ) {
            assertTrue activeMQ.getAttribute(JavaApp.SERVICE_UP)
        }
        assertFalse activeMQ.getAttribute(JavaApp.SERVICE_UP)
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
        activeMQ = new ActiveMQBroker(owner:app, queue:queueName);
        activeMQ.start([ testLocation ])
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
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory("tcp://${address}:${port}")
        Connection connection = factory.createConnection("admin", "activemq");
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
