package brooklyn.entity.messaging.activemq

import java.util.Collection
import java.util.Map

import javax.management.InstanceNotFoundException
import javax.management.ObjectName

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Entity
import brooklyn.entity.basic.Attributes
import brooklyn.entity.basic.JavaApp
import brooklyn.entity.messaging.JMSBroker
import brooklyn.entity.messaging.JMSDestination
import brooklyn.entity.messaging.Queue
import brooklyn.entity.messaging.Topic
import brooklyn.event.adapter.AttributePoller
import brooklyn.event.adapter.JmxSensorAdapter
import brooklyn.event.adapter.ValueProvider
import brooklyn.event.basic.ConfiguredAttributeSensor
import brooklyn.location.basic.SshMachineLocation
import brooklyn.util.SshBasedAppSetup

/**
 * An {@link brooklyn.entity.Entity} that represents a single ActiveMQ broker instance.
 */
public class ActiveMQBroker extends JMSBroker<ActiveMQQueue, ActiveMQTopic> {
    private static final Logger log = LoggerFactory.getLogger(ActiveMQBroker.class)

    public static final ConfiguredAttributeSensor<Integer> OPEN_WIRE_PORT = [ Integer, "openwire.port", "OpenWire port", 61616 ]

    public ActiveMQBroker(Map properties=[:], Entity owner=null) {
        super(properties, owner)

        setConfigIfValNonNull(OPEN_WIRE_PORT.configKey, properties.openWirePort)

        setConfigIfValNonNull(Attributes.JMX_USER.configKey, properties.user ?: "admin")
        setConfigIfValNonNull(Attributes.JMX_PASSWORD.configKey, properties.password ?: "activemq")
    }

    public void setBrokerUrl() {
        setAttribute(BROKER_URL, String.format("tcp://%s:%d/", getAttribute(HOSTNAME), getAttribute(OPEN_WIRE_PORT)))
    }

    public ActiveMQQueue createQueue(Map properties) {
        return new ActiveMQQueue(properties);
    }

    public ActiveMQTopic createTopic(Map properties) {
        return new ActiveMQTopic(properties);
    }

    public SshBasedAppSetup getSshBasedSetup(SshMachineLocation machine) {
        return ActiveMQSetup.newInstance(this, machine)
    }

    @Override
    public void addJmxSensors() {
        attributePoller.addSensor(JavaApp.SERVICE_UP, { computeNodeUp() } as ValueProvider)
    }

    @Override
    public Collection<String> toStringFieldsToInclude() {
        return super.toStringFieldsToInclude() + ['openWirePort']
    }

    protected boolean computeNodeUp() {
        String host = getAttribute(HOSTNAME)
        ValueProvider<String> provider = jmxAdapter.newAttributeProvider("org.apache.camel:context=${host}/camel,type=components,name=\"activemq\"", "State")
        try {
            String state = provider.compute()
            return (state == "Started")
        } catch (InstanceNotFoundException infe) {
            return false
        }
    }
}

public abstract class ActiveMQDestination extends JMSDestination {
    protected ObjectName broker

    public ActiveMQDestination(Map properties=[:], Entity owner=null) {
        super(properties, owner)
    }

    public void init() {
	    broker = new ObjectName("org.apache.activemq:Type=Broker,BrokerName=localhost")
    }
}

public class ActiveMQQueue extends ActiveMQDestination implements Queue {
    public ActiveMQQueue(Map properties=[:], Entity owner=null) {
        super(properties, owner)
    }

    @Override
    public void init() {
        setAttribute QUEUE_NAME, name
        super.init()
    }

    public void create() {
        jmxAdapter.operation(broker, "addQueue", name)
        addJmxSensors()
    }

    public void delete() {
        jmxAdapter.operation(broker, "removeQueue", name)
        removeJmxSensors()
    }

    public void addJmxSensors() {
        String queue = "org.apache.activemq:Type=Queue,BrokerName=localhost,Destination=${name}"
        attributePoller.addSensor(QUEUE_DEPTH_MESSAGES, jmxAdapter.newAttributeProvider(queue, "QueueSize"))
    }

    public void removeJmxSensors() {
        attributePoller.removeSensor(QUEUE_DEPTH_MESSAGES)
    }
}

public class ActiveMQTopic extends ActiveMQDestination implements Topic {
    public ActiveMQTopic(Map properties=[:], Entity owner=null) {
        super(properties, owner)
    }

    @Override
    public void init() {
        setAttribute TOPIC_NAME, name
        super.init()
    }

    public void create() {
        jmxAdapter.operation(broker, "addTopic", name)
        addJmxSensors()
    }

    public void delete() {
        jmxAdapter.operation(broker, "removeTopic", name)
        removeJmxSensors()
    }

    public void addJmxSensors() {
        //TODO add sensors for topics
    }

    public void removeJmxSensors() {
    }
}
