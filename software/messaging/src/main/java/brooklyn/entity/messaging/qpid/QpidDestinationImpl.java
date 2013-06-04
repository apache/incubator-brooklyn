package brooklyn.entity.messaging.qpid;

import static java.lang.String.format;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.messaging.amqp.AmqpServer;
import brooklyn.entity.messaging.jms.JMSDestinationImpl;
import brooklyn.event.feed.jmx.JmxFeed;
import brooklyn.event.feed.jmx.JmxHelper;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.flags.SetFromFlag;

public abstract class QpidDestinationImpl extends JMSDestinationImpl implements QpidDestination {
    public static final Logger log = LoggerFactory.getLogger(QpidDestination.class);
    
    @SetFromFlag
    String virtualHost;

    protected ObjectName virtualHostManager;
    protected ObjectName exchange;
    protected transient JmxHelper jmxHelper;
    protected volatile JmxFeed jmxFeed;

    public QpidDestinationImpl() {
    }

    @Override
    public QpidBroker getParent() {
        return (QpidBroker) super.getParent();
    }
    
    @Override
    public void onManagementStarting() {
        super.onManagementStarting();
        
        // TODO Would be nice to share the JmxHelper for all destinations, so just one connection.
        // But tricky for if brooklyn were distributed
        try {
            if (virtualHost == null) virtualHost = getConfig(QpidBroker.VIRTUAL_HOST_NAME);
            setAttribute(QpidBroker.VIRTUAL_HOST_NAME, virtualHost);
            virtualHostManager = new ObjectName(format("org.apache.qpid:type=VirtualHost.VirtualHostManager,VirtualHost=\"%s\"", virtualHost));
            jmxHelper = new JmxHelper((EntityLocal)getParent());
        } catch (MalformedObjectNameException e) {
            throw Exceptions.propagate(e);
        }
    }

    @Override
    protected void disconnectSensors() {
        if (jmxFeed != null) jmxFeed.stop();
    }

    @Override
    public void create() {
        jmxHelper.operation(virtualHostManager, "createNewQueue", getName(), getParent().getAttribute(Attributes.JMX_USER), true);
        jmxHelper.operation(exchange, "createNewBinding", getName(), getName());
        connectSensors();
    }
    
    @Override
    public void delete() {
        jmxHelper.operation(exchange, "removeBinding", getName(), getName());
        jmxHelper.operation(virtualHostManager, "deleteQueue", getName());
        disconnectSensors();
    }

    @Override
    public String getQueueName() {

        if (AmqpServer.AMQP_0_10.equals(getParent().getAmqpVersion())) {
            return String.format("'%s'/'%s'; { assert: never }", getExchangeName(), getName());
        } else {
            return getName();
        }
    }
}
