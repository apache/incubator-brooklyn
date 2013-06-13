package brooklyn.entity.messaging.qpid;

import brooklyn.entity.messaging.amqp.AmqpExchange;
import brooklyn.entity.messaging.jms.JMSDestination;

public interface QpidDestination extends JMSDestination, AmqpExchange {
    
    public void create();
    
    /**
     * Return the AMQP name for the queue.
     */
    public String getQueueName();
}
