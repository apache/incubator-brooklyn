package brooklyn.entity.messaging.qpid;

import static java.lang.String.format;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import brooklyn.entity.messaging.amqp.AmqpExchange;
import brooklyn.util.exceptions.Exceptions;

public class QpidTopicImpl extends QpidDestinationImpl implements QpidTopic {

    public QpidTopicImpl() {
    }

    @Override
    public void onManagementStarting() {
        super.onManagementStarting();
        setAttribute(TOPIC_NAME, getName());
        try {
            String virtualHost = getParent().getVirtualHost();
            exchange = new ObjectName(format("org.apache.qpid:type=VirtualHost.Exchange,VirtualHost=\"%s\",name=\"%s\",ExchangeType=topic", virtualHost, getExchangeName()));
        } catch (MalformedObjectNameException e) {
            throw Exceptions.propagate(e);
        }
    }

    // TODO sensors
    @Override
    public void connectSensors() {
    }

    @Override
    public String getExchangeName() { return AmqpExchange.TOPIC; }

    @Override
    public String getTopicName() { return getQueueName(); }
}
