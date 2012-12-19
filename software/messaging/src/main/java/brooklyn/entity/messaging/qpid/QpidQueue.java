package brooklyn.entity.messaging.qpid;

import static java.lang.String.format;

import java.util.Map;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import brooklyn.entity.Entity;
import brooklyn.entity.messaging.Queue;
import brooklyn.entity.messaging.amqp.AmqpExchange;
import brooklyn.event.adapter.JmxObjectNameAdapter;
import brooklyn.util.MutableMap;
import brooklyn.util.exceptions.Exceptions;

public class QpidQueue extends QpidDestination implements Queue {
    public QpidQueue() {
        this(MutableMap.of(), null);
    }
    public QpidQueue(Map properties) {
        this(properties, null);
    }
    public QpidQueue(Entity owner) {
        this(MutableMap.of(), owner);
    }
    public QpidQueue(Map properties, Entity owner) {
        super(properties, owner);
    }

    @Override
    public void init() {
        setAttribute(QUEUE_NAME, getName());
        super.init();
        try {
            exchange = new ObjectName(format("org.apache.qpid:type=VirtualHost.Exchange,VirtualHost=\"%s\",name=\"%s\",ExchangeType=direct", virtualHost, getExchangeName()));
        } catch (MalformedObjectNameException e) {
            throw Exceptions.propagate(e);
        }
    }

    public void connectSensors() {
        String queue = format("org.apache.qpid:type=VirtualHost.Queue,VirtualHost=\"%s\",name=\"%s\"", virtualHost, getName());
        JmxObjectNameAdapter queueAdapter = jmxAdapter.objectName(queue);
        queueAdapter.attribute("QueueDepth").poll(QUEUE_DEPTH_BYTES);
        queueAdapter.attribute("MessageCount").poll(QUEUE_DEPTH_MESSAGES);
    }

    /** {@inheritDoc} */
    public String getExchangeName() { return AmqpExchange.DIRECT; }
}
