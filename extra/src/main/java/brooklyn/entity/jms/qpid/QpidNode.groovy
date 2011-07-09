package brooklyn.entity.jms.qpid

import javax.management.InstanceNotFoundException
import javax.management.ObjectName

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.basic.AbstractEntity
import brooklyn.entity.basic.Attributes
import brooklyn.entity.basic.EntityLocal
import brooklyn.entity.basic.JavaApp
import brooklyn.entity.jms.Queue
import brooklyn.entity.jms.Topic
import brooklyn.event.adapter.AttributePoller
import brooklyn.event.adapter.JmxSensorAdapter
import brooklyn.event.adapter.ValueProvider
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.event.basic.BasicConfigKey
import brooklyn.location.Location
import brooklyn.location.basic.SshMachineLocation
import brooklyn.util.SshBasedJavaAppSetup

import com.google.common.base.Preconditions

/**
 * An {@link brooklyn.entity.Entity} that represents a single Qpid broker instance.
 */
public class QpidNode extends JavaApp {
    private static final Logger log = LoggerFactory.getLogger(QpidNode.class)

    public static final BasicConfigKey<Integer> SUGGESTED_AMQP_PORT = [Integer, "qpid.amqpPort", "Suggested AMQP port" ]
    public static final BasicConfigKey<String> VIRTUAL_HOST_NAME = [String, "qpid.virtualHost", "Qpid virtual host name" ]

    public static final BasicAttributeSensor<Integer> AMQP_PORT = Attributes.AMQP_PORT

    String virtualHost
    Collection<String> queueNames = []
    Collection<QpidQueue> queues = []
    Collection<String> topicNames = []
    Collection<QpidQueue> topics = []

    public QpidNode(Map properties=[:]) {
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

    public SshBasedJavaAppSetup getSshBasedSetup(SshMachineLocation machine) {
        return QpidSetup.newInstance(this, machine)
    }

    public void initJmxSensors() {
        attributePoller.addSensor(JavaApp.NODE_UP, { computeNodeUp() } as ValueProvider)
    }

    @Override
    public void start(Collection<Location> locations) {
        super.start(locations)

        queueNames.each { String name -> createQueue(name) }
        topicNames.each { String name -> createTopic(name) }
    }

    public void createQueue(String name, Map properties=[:]) {
        properties.owner = this
        properties.name = name
        properties.jmxAdapter = jmxAdapter
        queues += new QpidQueue(properties)
    }

    public void createTopic(String name, Map properties=[:]) {
        properties.owner = this
        properties.name = name
        properties.jmxAdapter = jmxAdapter
        topics += new QpidTopic(properties)
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

    protected EntityLocal broker
    protected ObjectName virtualHostManager
    protected ObjectName exchange

    protected transient JmxSensorAdapter jmxAdapter
    protected transient AttributePoller attributePoller

    public QpidBinding(Map properties=[:]) {
        super(properties)
        broker = owner

        Preconditions.checkNotNull properties.name, "Name must be specified"
        name = properties.name
 
        virtualHost = getConfig(QpidNode.VIRTUAL_HOST_NAME) ?: properties.virtualHost ?: "localhost"
        setConfig(QpidNode.VIRTUAL_HOST_NAME, virtualHost)
        virtualHostManager = new ObjectName("org.apache.qpid:type=VirtualHost.VirtualHostManager,VirtualHost=\"${virtualHost}\"")
        init()
        
        jmxAdapter = properties.jmxAdapter
        attributePoller = new AttributePoller(this)
 
        create()
        initJmxSensors()
    }
    
    public abstract void init()
 
    public abstract void initJmxSensors()
 
    public void create() {
        jmxAdapter.operation(virtualHostManager, "createNewQueue", name, broker.getAttribute(Attributes.JMX_USER), true)
        jmxAdapter.operation(exchange, "createNewBinding", name, name)
    }
 
    public void delete() {
        jmxAdapter.operation(exchange, "removeBinding", name, name)
        jmxAdapter.operation(virtualHostManager, "deleteQueue", name)
    }
 
    @Override
    public Collection<String> toStringFieldsToInclude() {
        return super.toStringFieldsToInclude() + ['name']
    }
}

public class QpidQueue extends QpidBinding implements Queue {
    public QpidQueue(Map properties=[:]) {
        super(properties)
    }
    
    public void init() {
        setConfig QUEUE_NAME, name
        exchange = new ObjectName("org.apache.qpid:type=VirtualHost.Exchange,VirtualHost=\"${virtualHost}\",name=\"amq.direct\",ExchangeType=\"direct\"")
    }
 
    public void initJmxSensors() {
        String queue = "org.apache.qpid:type=VirtualHost.Queue,VirtualHost=\"${virtualHost}\",name=\"${name}\""
        attributePoller.addSensor(QUEUE_DEPTH, jmxAdapter.newAttributeProvider(queue, "QueueDepth"))
        attributePoller.addSensor(MESSAGE_COUNT, jmxAdapter.newAttributeProvider(queue, "MessageCount"))
    }
}

public class QpidTopic extends QpidBinding implements Topic {
    public QpidTopic(Map properties=[:]) {
        super(properties)
    }
    
    public void init() {
        setConfig TOPIC_NAME, name
        exchange = new ObjectName("org.apache.qpid:type=VirtualHost.Exchange,VirtualHost=\"${virtualHost}\",name=\"amq.topic\",ExchangeType=\"topic\"")
    }
 
    public void initJmxSensors() {
        String topic = "org.apache.qpid:type=VirtualHost.Queue,VirtualHost=\"${virtualHost}\",name=\"${name}\""
        attributePoller.addSensor(QUEUE_DEPTH, jmxAdapter.newAttributeProvider(topic, "QueueDepth"))
        attributePoller.addSensor(MESSAGE_COUNT, jmxAdapter.newAttributeProvider(topic, "MessageCount"))
    }
}
