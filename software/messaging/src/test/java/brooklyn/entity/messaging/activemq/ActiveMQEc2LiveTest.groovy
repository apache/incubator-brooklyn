package brooklyn.entity.messaging.activemq

import static brooklyn.test.TestUtils.executeUntilSucceeds
import static brooklyn.test.TestUtils.executeUntilSucceedsWithShutdown
import static org.testng.Assert.*

import javax.jms.Connection
import javax.jms.MessageConsumer
import javax.jms.MessageProducer
import javax.jms.Session
import javax.jms.TextMessage

import org.apache.activemq.ActiveMQConnectionFactory
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.basic.ApplicationBuilder
import brooklyn.config.BrooklynProperties
import brooklyn.entity.basic.Entities
import brooklyn.entity.proxying.BasicEntitySpec
import brooklyn.entity.trait.Startable
import brooklyn.location.Location
import brooklyn.location.basic.BasicLocationRegistry
import brooklyn.test.entity.TestApplication
import brooklyn.util.internal.TimeExtras

class ActiveMQEc2LiveTest {
    protected static final Logger LOG = LoggerFactory.getLogger(ActiveMQEc2LiveTest.class)

    static { TimeExtras.init() }

    Location loc;
    TestApplication app
    ActiveMQBroker activeMQ

    @BeforeMethod(alwaysRun=true)
    public void setUp() {
        app = ApplicationBuilder.builder(TestApplication.class).manage();
        Entities.manage(app)

        BrooklynProperties props = BrooklynProperties.Factory.newDefault()
        props.put("brooklyn.location.jclouds.aws-ec2.image-id", "eu-west-1/ami-89def4fd")
        props.put("brooklyn.location.jclouds.aws-ec2.image-owner", "411009282317")

        loc = new BasicLocationRegistry(props).resolve("aws-ec2:eu-west-1")
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() {
        if (app != null) Entities.destroyAll(app);
    }

    /**
     * Test that the broker starts up and sets SERVICE_UP correctly.
     */
    @Test(groups = [ "Live" ])
    public void canStartupAndShutdown() {
        activeMQ = app.createAndManageChild(BasicEntitySpec.newInstance(ActiveMQBroker.class));
        
        app.start([ loc ])
        executeUntilSucceedsWithShutdown(activeMQ) {
            assertTrue activeMQ.getAttribute(Startable.SERVICE_UP)
        }
        assertFalse activeMQ.getAttribute(Startable.SERVICE_UP)
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
        activeMQ = app.createAndManageChild(BasicEntitySpec.newInstance(ActiveMQBroker.class)
                .configure("queue", queueName));
        
        app.start([ loc ])
        executeUntilSucceeds {
            assertTrue activeMQ.getAttribute(Startable.SERVICE_UP)
        }

        try {
            // Check queue created
            assertFalse activeMQ.queueNames.isEmpty()
            assertEquals activeMQ.queueNames.size(), 1
            assertTrue activeMQ.queueNames.contains(queueName)
            assertEquals activeMQ.children.size(), 1
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
