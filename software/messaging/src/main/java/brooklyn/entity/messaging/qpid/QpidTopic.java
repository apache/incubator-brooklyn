package brooklyn.entity.messaging.qpid;

import static java.lang.String.format;

import java.util.Map;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import brooklyn.entity.Entity;
import brooklyn.entity.messaging.Topic;
import brooklyn.entity.messaging.amqp.AmqpExchange;
import brooklyn.util.MutableMap;
import brooklyn.util.exceptions.Exceptions;

public class QpidTopic extends QpidDestination implements Topic {

    public QpidTopic() {
        super(MutableMap.of(), null);
    }
    public QpidTopic(Map properties) {
        super(properties, null);
    }
    public QpidTopic(Entity parent) {
        super(MutableMap.of(), parent);
    }
    public QpidTopic(Map properties, Entity parent) {
        super(properties, parent);
    }

    @Override
    public void init() {
        setAttribute(TOPIC_NAME, getName());
        super.init();
        try {
            String virtualHost = getParent().getVirtualHost();
            exchange = new ObjectName(format("org.apache.qpid:type=VirtualHost.Exchange,VirtualHost=\"%s\",name=\"%s\",ExchangeType=topic", virtualHost, getExchangeName()));
        } catch (MalformedObjectNameException e) {
            throw Exceptions.propagate(e);
        }
    }

    // TODO sensors
    public void connectSensors() {
    }

    /** {@inheritDoc} */
    public String getExchangeName() { return AmqpExchange.TOPIC; }

    public String getTopicName() { return getQueueName(); }
}
