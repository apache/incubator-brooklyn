package brooklyn.entity.messaging.activemq;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import javax.jms.Connection;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.testng.annotations.Test;

import brooklyn.entity.AbstractEc2LiveTest;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.trait.Startable;
import brooklyn.location.Location;
import brooklyn.test.EntityTestUtils;

import com.google.common.collect.ImmutableList;

public class ActiveMQEc2LiveTest extends AbstractEc2LiveTest {

    /**
     * Test that can install+start, and use, ActiveMQ.
     */
    @Override
    protected void doTest(Location loc) throws Exception {
        String queueName = "testQueue";
        int number = 10;
        String content = "01234567890123456789012345678901";

        // Start broker with a configured queue
        ActiveMQBroker activeMQ = app.createAndManageChild(EntitySpec.create(ActiveMQBroker.class).configure("queue", queueName));
        
        app.start(ImmutableList.of(loc));
        
        EntityTestUtils.assertAttributeEqualsEventually(activeMQ, Startable.SERVICE_UP, true);

        // Check queue created
        assertEquals(ImmutableList.copyOf(activeMQ.getQueueNames()), ImmutableList.of(queueName));
        assertEquals(activeMQ.getChildren().size(), 1);
        assertEquals(activeMQ.getQueues().size(), 1);

        // Get the named queue entity
        ActiveMQQueue queue = activeMQ.getQueues().get(queueName);
        assertNotNull(queue);

        // Connect to broker using JMS and send messages
        Connection connection = getActiveMQConnection(activeMQ);
        clearQueue(connection, queueName);
        EntityTestUtils.assertAttributeEqualsEventually(queue, ActiveMQQueue.QUEUE_DEPTH_MESSAGES, 0);
        sendMessages(connection, number, queueName, content);

        // Check messages arrived
        EntityTestUtils.assertAttributeEqualsEventually(queue, ActiveMQQueue.QUEUE_DEPTH_MESSAGES, number);

        connection.close();
    }

    private Connection getActiveMQConnection(ActiveMQBroker activeMQ) throws Exception {
        int port = activeMQ.getAttribute(ActiveMQBroker.OPEN_WIRE_PORT);
        String address = activeMQ.getAttribute(ActiveMQBroker.ADDRESS);
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(String.format("tcp://%s:%s", address, port));
        Connection connection = factory.createConnection("admin", "activemq");
        connection.start();
        return connection;
    }

    private void sendMessages(Connection connection, int count, String queueName, String content) throws Exception {
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        org.apache.activemq.command.ActiveMQQueue destination = (org.apache.activemq.command.ActiveMQQueue) session.createQueue(queueName);
        MessageProducer messageProducer = session.createProducer(destination);

        for (int i = 0; i < count; i++) {
            TextMessage message = session.createTextMessage(content);
            messageProducer.send(message);
        }

        session.close();
    }

    private int clearQueue(Connection connection, String queueName) throws Exception {
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        org.apache.activemq.command.ActiveMQQueue destination = (org.apache.activemq.command.ActiveMQQueue) session.createQueue(queueName);
        MessageConsumer messageConsumer = session.createConsumer(destination);

        int received = 0;
        while (messageConsumer.receive(500) != null) received++;

        session.close();

        return received;
    }
    
    @Test(enabled=false)
    public void testDummy() {} // Convince testng IDE integration that this really does have test methods  
}
