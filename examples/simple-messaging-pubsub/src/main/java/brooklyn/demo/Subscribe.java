package brooklyn.demo;

import javax.jms.Connection;
import javax.jms.MessageConsumer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.qpid.client.AMQConnectionFactory;
import org.apache.qpid.configuration.ClientProperties;

import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;

/** Receives messages from a queue on a Qpid broker at a given URL. */
public class Subscribe {
    public static final String QUEUE = "'amq.direct'/'testQueue'; { node: { type: queue } }";
    private static final long MESSAGE_TIMEOUT_MILLIS = 15000L;
    private static final int MESSAGE_COUNT = 100;
    
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

        System.out.printf("Waiting up to %s milliseconds to receive %s messages\n", MESSAGE_TIMEOUT_MILLIS, MESSAGE_COUNT);
        try {
            // Create a producer for the queue
            Queue destination = session.createQueue(QUEUE);
            MessageConsumer messageConsumer = session.createConsumer(destination);

            // Try and receive 100 messages
            int n = MESSAGE_COUNT;
            do {
                TextMessage msg = (TextMessage) messageConsumer.receive(MESSAGE_TIMEOUT_MILLIS);
                if (msg == null) break;
                System.out.printf("Got message: '%s'\n", msg.getText());
            } while (n --> 0);
        } catch (Exception e) {
            System.err.printf("Error while receiving - %s\n", e.getMessage());
            System.err.printf("Cause: %s\n", Throwables.getStackTraceAsString(e));
        } finally {
            session.close();
            connection.close();
        }
    }
}
