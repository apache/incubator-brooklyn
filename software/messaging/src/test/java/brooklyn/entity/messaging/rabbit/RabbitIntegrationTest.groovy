package brooklyn.entity.messaging.rabbit;

import static brooklyn.test.TestUtils.*
import static java.util.concurrent.TimeUnit.*
import static org.testng.Assert.*

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.basic.Entities
import brooklyn.entity.messaging.MessageBroker
import brooklyn.entity.messaging.amqp.AmqpExchange
import brooklyn.entity.trait.Startable
import brooklyn.location.Location
import brooklyn.location.basic.LocalhostMachineProvisioningLocation
import brooklyn.test.entity.TestApplication
import brooklyn.util.internal.TimeExtras

import com.google.common.base.Charsets
import com.google.common.collect.Maps
import com.rabbitmq.client.Channel
import com.rabbitmq.client.Connection
import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.QueueingConsumer

/**
 * Test the operation of the {@link RabbitBroker} class.
 */
public class RabbitIntegrationTest {
    private static final Logger log = LoggerFactory.getLogger(RabbitIntegrationTest.class)

    static { TimeExtras.init() }

    private TestApplication app
    private Location testLocation
    private RabbitBroker rabbit

    @BeforeMethod(groups = "Integration")
    public void setup() {
        app = new TestApplication();
        testLocation = new LocalhostMachineProvisioningLocation()
    }

    @AfterMethod(alwaysRun = true)
    public void shutdown() {
        if (app != null) Entities.destroy(app);
    }

    /**
     * Test that the broker starts up and sets SERVICE_UP correctly.
     */
    @Test(groups = "Integration")
    public void canStartupAndShutdown() {
        rabbit = new RabbitBroker(owner:app);
        Entities.startManagement(app);
        rabbit.start([ testLocation ])
        executeUntilSucceedsWithShutdown(rabbit) {
            assertTrue rabbit.getAttribute(Startable.SERVICE_UP)
        }
        assertFalse rabbit.getAttribute(Startable.SERVICE_UP)
    }

    /**
     * Test that an AMQP client can connect to and use the broker.
     */
    @Test(groups = "Integration")
    public void testClientConnection() {
        rabbit = new RabbitBroker(owner:app);
        Entities.startManagement(app);
        rabbit.start([ testLocation ])
        executeUntilSucceeds {
            assertTrue rabbit.getAttribute(Startable.SERVICE_UP)
        }

        byte[] content = "MessageBody".getBytes(Charsets.UTF_8)
        String queue = "queueName"
        Channel producer, consumer
        try {
	        producer = getAmqpChannel(rabbit)
	        consumer = getAmqpChannel(rabbit)

	        producer.queueDeclare(queue, true, false, false, Maps.newHashMap())
	        producer.queueBind(queue, AmqpExchange.DIRECT, queue)
	        producer.basicPublish(AmqpExchange.DIRECT, queue, null, content)
            
            QueueingConsumer queueConsumer = new QueueingConsumer(consumer);
            consumer.basicConsume(queue, true, queueConsumer);
        
            QueueingConsumer.Delivery delivery = queueConsumer.nextDelivery();
            assertEquals(delivery.body, content)
        } finally {
	        producer?.close()
	        consumer?.close()
        }
    }

    private Channel getAmqpChannel(RabbitBroker rabbit) {
        String uri = rabbit.getAttribute(MessageBroker.BROKER_URL)
        log.warn("connecting to rabbit {}", uri)
        ConnectionFactory factory = new ConnectionFactory()
        factory.setUri(uri)
        Connection conn = factory.newConnection()
        Channel channel = conn.createChannel()
        return channel;
    }
}
