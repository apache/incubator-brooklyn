package brooklyn.demo

import javax.jms.Connection
import javax.jms.MessageConsumer
import javax.jms.Queue
import javax.jms.Session
import javax.jms.TextMessage

import org.apache.qpid.client.AMQConnectionFactory
import org.apache.qpid.configuration.ClientProperties
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import com.google.common.base.Preconditions

/** Receives messages from a queue on a Qpid broker at a given URL. */
public class Subscribe {
    public static final Logger LOG = LoggerFactory.getLogger(Subscribe)

    public static final String QUEUE = "amq.direct/testQueue; {assert: always, node:{ type: queue }}"

    public static void main(String...argv) {
        Preconditions.checkElementIndex(0, argv.length, "Must specify broker URL")
        String url = argv[0]
        
        System.setProperty(ClientProperties.AMQP_VERSION, "0-10")
        System.setProperty(ClientProperties.DEST_SYNTAX, "ADDR")
        AMQConnectionFactory factory = new AMQConnectionFactory(url)
        Connection connection = factory.createConnection();
        connection.start()
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)
        Queue destination = session.createQueue(QUEUE)
        MessageConsumer messageConsumer = session.createConsumer(destination)

        int n = 0
        while (true) {
            TextMessage msg = messageConsumer.receive(15000L)
            if (msg == null) break
            LOG.info("got message ${++n} ${msg.text}")
        }

        session.close()
        connection.close()
    }
}
