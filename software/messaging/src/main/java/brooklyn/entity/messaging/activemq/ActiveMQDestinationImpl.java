package brooklyn.entity.messaging.activemq;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.messaging.jms.JMSDestinationImpl;
import brooklyn.event.feed.jmx.JmxFeed;
import brooklyn.event.feed.jmx.JmxHelper;
import brooklyn.util.exceptions.Exceptions;

public abstract class ActiveMQDestinationImpl extends JMSDestinationImpl implements ActiveMQDestination {
    protected ObjectName brokerMBeanName;
    protected transient JmxHelper jmxHelper;
    protected volatile JmxFeed jmxFeed;

    public ActiveMQDestinationImpl() {
    }
    
    @Override
    public void onManagementStarting() {
        super.onManagementStarting();
        
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
