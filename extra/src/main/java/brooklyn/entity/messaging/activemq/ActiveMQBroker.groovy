package brooklyn.entity.messaging.activemq

import java.util.Collection
import java.util.Map

import javax.management.InstanceNotFoundException
import javax.management.ObjectName

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Entity
import brooklyn.entity.basic.AbstractEntity
import brooklyn.entity.basic.Attributes
import brooklyn.entity.basic.JavaApp
import brooklyn.entity.messaging.Queue
import brooklyn.entity.messaging.Topic
import brooklyn.event.adapter.AttributePoller
import brooklyn.event.adapter.JmxSensorAdapter
import brooklyn.event.adapter.ValueProvider
import brooklyn.event.basic.ConfiguredAttributeSensor
import brooklyn.location.Location
import brooklyn.location.basic.SshMachineLocation
import brooklyn.util.SshBasedAppSetup

import com.google.common.base.Preconditions

/**
 * An {@link brooklyn.entity.Entity} that represents a single ActiveMQ broker instance.
 */
public class ActiveMQBroker extends JavaApp {
    private static final Logger log = LoggerFactory.getLogger(ActiveMQBroker.class)

    public static final ConfiguredAttributeSensor<Integer> OPEN_WIRE_PORT = [ Integer, "openwire.port", "OpenWire port", 61616 ]

    Collection<String> queueNames = []
    Map<String, ActiveMQQueue> queues = [:]
    Collection<String> topicNames = []
    Map<String, ActiveMQTopic> topics = [:]

    public ActiveMQBroker(Map properties=[:]) {
        super(properties)

        setConfigIfValNonNull(OPEN_WIRE_PORT.configKey, properties.openWirePort)

        setConfigIfValNonNull(Attributes.JMX_USER.configKey, properties.user ?: "admin")
        setConfigIfValNonNull(Attributes.JMX_PASSWORD.configKey, properties.password ?: "activemq")

        if (properties.queue) queueNames.add properties.queue
        if (properties.queues) queueNames.addAll properties.queues

        if (properties.topic) topicNames.add properties.topic
        if (properties.topics) topicNames.addAll properties.topics
    }

    public SshBasedAppSetup getSshBasedSetup(SshMachineLocation machine) {
        return ActiveMQSetup.newInstance(this, machine)
    }

    @Override
    public void addJmxSensors() {
        attributePoller.addSensor(JavaApp.SERVICE_UP, { computeNodeUp() } as ValueProvider)
    }

    @Override
    public void postStart() {
        queueNames.each { String name -> createQueue(name) }
        topicNames.each { String name -> createTopic(name) }
    }

    @Override
    public void preStop() {
        queues.each { String name, ActiveMQQueue queue -> queue.destroy() }
        topics.each { String name, ActiveMQTopic topic -> topic.destroy() }
    }

    public void createQueue(String name, Map properties=[:]) {
        properties.owner = this
        properties.name = name
        queues.put name, new ActiveMQQueue(properties)
    }

    public void createTopic(String name, Map properties=[:]) {
        properties.owner = this
        properties.name = name
        topics.put name, new ActiveMQTopic(properties)
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

    protected boolean computeVersion() {
        ValueProvider<String> provider = jmxAdapter.newAttributeProvider("org.apache.activemq:type=Broker,BrokerName=localhost", "BrokerVersion")
        try {
            String productVersion = provider.compute()
            log.error("*** activemq version iz {} ***", productVersion)
            return (productVersion == getAttribute(Attributes.VERSION))
        } catch (InstanceNotFoundException infe) {
            return false
        }
    }
}

public abstract class ActiveMQBinding extends AbstractEntity {
    String virtualHost

    protected ObjectName broker

    transient JmxSensorAdapter jmxAdapter
    transient AttributePoller attributePoller

    public ActiveMQBinding(Map properties=[:], Entity owner=null) {
        super(properties, owner)

        Preconditions.checkNotNull name, "Name must be specified"

        broker = new ObjectName("org.apache.activemq:Type=Broker,BrokerName=localhost")
        init()

        jmxAdapter = ((ActiveMQBroker) getOwner()).jmxAdapter
        attributePoller = new AttributePoller(this)

        create()
    }

    public abstract void init();

    public abstract void addJmxSensors()

    public abstract void removeJmxSensors()

    public abstract void create();

    public abstract void delete();

    @Override
    public void destroy() {
		attributePoller.close()
        super.destroy()
	}

    @Override
    public Collection<String> toStringFieldsToInclude() {
        return super.toStringFieldsToInclude() + ['name']
    }
}

public class ActiveMQQueue extends ActiveMQBinding implements Queue {
    public ActiveMQQueue(Map properties=[:], Entity owner=null) {
        super(properties, owner)
    }

    @Override
    public void init() {
        setAttribute QUEUE_NAME, name
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
        attributePoller.addSensor(MESSAGE_COUNT, jmxAdapter.newAttributeProvider(queue, "QueueSize"))
    }

    public void removeJmxSensors() {
        attributePoller.removeSensor(MESSAGE_COUNT)
    }
}

public class ActiveMQTopic extends ActiveMQBinding implements Topic {
    public ActiveMQTopic(Map properties=[:], Entity owner=null) {
        super(properties, owner)
    }

    @Override
    public void init() {
        setAttribute TOPIC_NAME, name
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
        String topic = "org.apache.activemq:Type=Topic,BrokerName=localhost,Destination=${name}"
        attributePoller.addSensor(MESSAGE_COUNT, jmxAdapter.newAttributeProvider(topic, "MessageCount"))
    }

    public void removeJmxSensors() {
        attributePoller.removeSensor(MESSAGE_COUNT)
    }
}
