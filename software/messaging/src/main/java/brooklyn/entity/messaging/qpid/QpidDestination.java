package brooklyn.entity.messaging.qpid;

import static java.lang.String.format;

import java.io.IOException;
import java.util.Map;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.messaging.amqp.AmqpExchange;
import brooklyn.entity.messaging.amqp.AmqpServer;
import brooklyn.entity.messaging.jms.JMSDestination;
import brooklyn.event.adapter.JmxHelper;
import brooklyn.event.adapter.JmxSensorAdapter;
import brooklyn.event.adapter.SensorRegistry;
import brooklyn.util.MutableMap;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.flags.SetFromFlag;

public abstract class QpidDestination extends JMSDestination implements AmqpExchange {
    public static final Logger log = LoggerFactory.getLogger(QpidDestination.class);
    
    @SetFromFlag
    String virtualHost;

    protected ObjectName virtualHostManager;
    protected ObjectName exchange;
    protected transient SensorRegistry sensorRegistry;
    protected transient JmxHelper helper;
    protected transient JmxSensorAdapter jmxAdapter;

    public QpidDestination() {
        this(MutableMap.of(), null);
    }
    public QpidDestination(Entity owner) {
        this(MutableMap.of(), owner);
    }
    public QpidDestination(Map properties) {
        this(properties, null);
    }
    public QpidDestination(Map properties, Entity owner) {
        super(properties, owner);
    }

    @Override
    public QpidBroker getOwner() {
        return (QpidBroker) super.getOwner();
    }
    
    public void init() {
        // TODO Would be nice to share the JmxHelper for all destinations, so just one connection.
        // But tricky for if brooklyn were distributed
        try {
            if (virtualHost == null) virtualHost = getConfig(QpidBroker.VIRTUAL_HOST_NAME);
            setAttribute(QpidBroker.VIRTUAL_HOST_NAME, virtualHost);
            virtualHostManager = new ObjectName(format("org.apache.qpid:type=VirtualHost.VirtualHostManager,VirtualHost=\"%s\"", virtualHost));
            if (sensorRegistry == null) sensorRegistry = new SensorRegistry(this);
            helper = new JmxHelper((EntityLocal)getOwner());
            helper.connect();
            jmxAdapter = sensorRegistry.register(new JmxSensorAdapter(helper));
        } catch (MalformedObjectNameException e) {
            throw Exceptions.propagate(e);
        } catch (IOException e) {
            throw Exceptions.propagate(e);
        }
    }

    public void create() {
        helper.operation(virtualHostManager, "createNewQueue", getName(), getOwner().getAttribute(Attributes.JMX_USER), true);
        helper.operation(exchange, "createNewBinding", getName(), getName());
        connectSensors();
        sensorRegistry.activateAdapters();
    }
    
    public void delete() {
        helper.operation(exchange, "removeBinding", getName(), getName());
        helper.operation(virtualHostManager, "deleteQueue", getName());
        sensorRegistry.deactivateAdapters();
    }

    /**
     * Return the AMQP name for the queue.
     */
    public String getQueueName() {

        if (AmqpServer.AMQP_0_10.equals(getOwner().getAmqpVersion())) {
            return String.format("'%s'/'%s'; { assert: never }", getExchangeName(), getName());
        } else {
            return getName();
        }
    }
}
