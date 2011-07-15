package brooklyn.entity.messaging.qpid

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
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.event.basic.BasicConfigKey
import brooklyn.location.Location
import brooklyn.location.basic.SshMachineLocation
import brooklyn.util.SshBasedAppSetup

import com.google.common.base.Preconditions

/**
 * An {@link brooklyn.entity.Entity} that represents a single Qpid broker instance.
 */
public class QpidBroker extends JavaApp {
    private static final Logger log = LoggerFactory.getLogger(QpidBroker.class)

    public static final BasicConfigKey<Integer> SUGGESTED_AMQP_PORT = [Integer, "qpid.amqpPort", "Suggested AMQP port" ]
    public static final BasicConfigKey<String> VIRTUAL_HOST_NAME = [String, "qpid.virtualHost", "Qpid virtual host name" ]

    public static final BasicAttributeSensor<Integer> AMQP_PORT = Attributes.AMQP_PORT

    String virtualHost
    Collection<String> queueNames = []
    Map<String,QpidQueue> queues = [:]
    Collection<String> topicNames = []
    Map<String,QpidTopic> topics = [:]

    public QpidBroker(Map properties=[:]) {
        super(properties)
        virtualHost = getConfig(VIRTUAL_HOST_NAME) ?: properties.virtualHost ?: "localhost"
        setConfig(VIRTUAL_HOST_NAME, virtualHost)

        setAttribute(Attributes.JMX_USER, properties.user ?: "admin")
        setAttribute(Attributes.JMX_PASSWORD, properties.password ?: "admin")

        if (properties.queue) queueNames.add properties.queue
        if (properties.queues) queueNames.addAll properties.queues

        if (properties.topic) topicNames.add properties.topic
        if (properties.topics) topicNames.addAll properties.topics
    }

    public SshBasedAppSetup getSshBasedSetup(SshMachineLocation machine) {
        return QpidSetup.newInstance(this, machine)
    }

    public void initJmxSensors() {
        attributePoller.addSensor(JavaApp.SERVICE_UP, { computeNodeUp() } as ValueProvider)
    }

    @Override
    public void start(Collection<Location> locations) {
        super.start(locations)

        queueNames.each { String name -> createQueue(name) }
        topicNames.each { String name -> createTopic(name) }
    }

    @Override
    public void stop() {
        queues.each { String name, QpidQueue queue -> queue.destroy() }
        topics.each { String name, QpidTopic topic -> topic.destroy() }

        super.stop()
    }

    public void createQueue(String name, Map properties=[:]) {
        properties.owner = this
        properties.name = name
        queues.put name, new QpidQueue(properties)
    }

    public void createTopic(String name, Map properties=[:]) {
        properties.owner = this
        properties.name = name
        topics.put name, new QpidTopic(properties)
    }

    @Override
    public Collection<String> toStringFieldsToInclude() {
        return super.toStringFieldsToInclude() + ['amqpPort']
    }

    protected boolean computeNodeUp() {
        ValueProvider<String> provider = jmxAdapter.newAttributeProvider("org.apache.qpid:type=ServerInformation,name=ServerInformation", "ProductVersion")
        try {
            String productVersion = provider.compute()
	        return (productVersion == getAttribute(Attributes.VERSION))
        } catch (InstanceNotFoundException infe) {
            return false
        }
    }
}

public abstract class QpidBinding extends AbstractEntity {
    String virtualHost
    String name

    protected ObjectName virtualHostManager
    protected ObjectName exchange

    transient JmxSensorAdapter jmxAdapter
    transient AttributePoller attributePoller

    public QpidBinding(Map properties=[:], Entity owner=null) {
        super(properties, owner)

        Preconditions.checkNotNull properties.name, "Name must be specified"
        name = properties.name

        virtualHost = getConfig(QpidBroker.VIRTUAL_HOST_NAME) ?: properties.virtualHost ?: "localhost"
        setConfig(QpidBroker.VIRTUAL_HOST_NAME, virtualHost)
        virtualHostManager = new ObjectName("org.apache.qpid:type=VirtualHost.VirtualHostManager,VirtualHost=\"${virtualHost}\"")
        init()

        jmxAdapter = ((QpidBroker) this.owner).jmxAdapter
        attributePoller = new AttributePoller(this)

        create()
    }

    public abstract void init()

    public abstract void addJmxSensors()

    public abstract void removeJmxSensors()

    public void create() {
        jmxAdapter.operation(virtualHostManager, "createNewQueue", name, owner.getAttribute(Attributes.JMX_USER), true)
        jmxAdapter.operation(exchange, "createNewBinding", name, name)
        addJmxSensors()
    }

    public void delete() {
        jmxAdapter.operation(exchange, "removeBinding", name, name)
        jmxAdapter.operation(virtualHostManager, "deleteQueue", name)
        removeJmxSensors()
    }

    /**
     * Return the Qpid BURL name for the queue.
     */
    public String getBindingUrl() { return String.format("BURL:%s", name) }

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

public class QpidQueue extends QpidBinding implements Queue {
    public QpidQueue(Map properties=[:], Entity owner=null) {
        super(properties, owner)
    }

    public void init() {
        setConfig QUEUE_NAME, name
        exchange = new ObjectName("org.apache.qpid:type=VirtualHost.Exchange,VirtualHost=\"${virtualHost}\",name=\"amq.direct\",ExchangeType=direct")
    }

    public void addJmxSensors() {
        String queue = "org.apache.qpid:type=VirtualHost.Queue,VirtualHost=\"${virtualHost}\",name=\"${name}\""
        attributePoller.addSensor(QUEUE_DEPTH, jmxAdapter.newAttributeProvider(queue, "QueueDepth"))
        attributePoller.addSensor(MESSAGE_COUNT, jmxAdapter.newAttributeProvider(queue, "MessageCount"))
    }

    public void removeJmxSensors() {
        String queue = "org.apache.qpid:type=VirtualHost.Queue,VirtualHost=\"${virtualHost}\",name=\"${name}\""
        attributePoller.removeSensor(QUEUE_DEPTH)
        attributePoller.removeSensor(MESSAGE_COUNT)
    }
}

public class QpidTopic extends QpidBinding implements Topic {
    public QpidTopic(Map properties=[:], Entity owner=null) {
        super(properties, owner)
    }

    public void init() {
        setConfig TOPIC_NAME, name
        exchange = new ObjectName("org.apache.qpid:type=VirtualHost.Exchange,VirtualHost=\"${virtualHost}\",name=\"amq.topic\",ExchangeType=topic")
    }

    public void addJmxSensors() {
        String topic = "org.apache.qpid:type=VirtualHost.Queue,VirtualHost=\"${virtualHost}\",name=\"${name}\""
        attributePoller.addSensor(QUEUE_DEPTH, jmxAdapter.newAttributeProvider(topic, "QueueDepth"))
        attributePoller.addSensor(MESSAGE_COUNT, jmxAdapter.newAttributeProvider(topic, "MessageCount"))
    }

    public void removeJmxSensors() {
        String topic = "org.apache.qpid:type=VirtualHost.Queue,VirtualHost=\"${virtualHost}\",name=\"${name}\""
        attributePoller.removeSensor(QUEUE_DEPTH)
        attributePoller.removeSensor(MESSAGE_COUNT)
    }
}
