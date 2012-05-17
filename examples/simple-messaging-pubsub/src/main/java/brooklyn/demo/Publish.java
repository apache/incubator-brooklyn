package brooklyn.demo;

import javax.jms.Connection;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.qpid.client.AMQConnectionFactory;
import org.apache.qpid.configuration.ClientProperties;

import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;

/** Publishes messages to a queue on a Qpid broker at a given URL. */
public class Publish {
    public static final String QUEUE = "'amq.direct'/'testQueue'; { node: { type: queue } }";
        
    public static void main(String...argv) throws Exception {
        Preconditions.checkElementIndex(0, argv.length, "Must specify broker URL");
        String url = argv[0];
        
        // Set Qpid client properties
        System.setProperty(ClientProperties.AMQP_VERSION, "0-10");
        System.setProperty(ClientProperties.DEST_SYNTAX, "ADDR");

        // Connect to the broker
        AMQConnectionFactory factory = new AMQConnectionFactory(url);
        Connection connection = factory.createConnection();
        connection.start();
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        
        try {
            // Create a producer for the queue
	        Queue destination = session.createQueue(QUEUE);
	        MessageProducer messageProducer = session.createProducer(destination);

	        // Send 100 messages
	        for (int n = 0; n < 100; n++) {
	            String body = String.format("test message %03d", n);
	            TextMessage message = session.createTextMessage(body);
	            messageProducer.send(message);
	            System.out.printf("Sent message %s\n", body);
	        }
        } catch (Exception e) {
            System.err.printf("Error while sending - %s\n", e.getMessage());
            System.err.printf("Cause: %s\n", Throwables.getStackTraceAsString(e));
        } finally {
            session.close();
            connection.close();
        }
    }
}
