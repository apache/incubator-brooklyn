package brooklyn.entity.messaging.activemq;

import java.util.Map;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.messaging.jms.JMSDestination;
import brooklyn.event.feed.jmx.JmxFeed;
import brooklyn.event.feed.jmx.JmxHelper;
import brooklyn.util.MutableMap;
import brooklyn.util.exceptions.Exceptions;

public abstract class ActiveMQDestination extends JMSDestination {
    protected ObjectName brokerMBeanName;
    protected transient JmxHelper jmxHelper;
    protected JmxFeed jmxFeed;

    public ActiveMQDestination() {
        this(MutableMap.of(), null);
    }
    public ActiveMQDestination(Map properties) {
        this(properties, null);
    }
    public ActiveMQDestination(Entity parent) {
        this(MutableMap.of(), parent);
    }
    public ActiveMQDestination(Map properties, Entity parent) {
        super(properties, parent);
    }
    
    public void init() {
        //assume just one BrokerName at this endpoint
        try {
            brokerMBeanName = new ObjectName("org.apache.activemq:BrokerName=localhost,Type=Broker");
            jmxHelper = new JmxHelper((EntityLocal) getParent());
        } catch (MalformedObjectNameException e) {
            throw Exceptions.propagate(e);
        }
    }
    
    @Override
    protected void disconnectSensors() {
        if (jmxFeed != null) jmxFeed.stop();
    }
}
