package brooklyn.entity.messaging.qpid

import java.util.Collection
import java.util.Map

import javax.management.InstanceNotFoundException
import javax.management.ObjectName

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Entity
import brooklyn.entity.basic.Attributes
import brooklyn.entity.basic.JavaApp
import brooklyn.entity.basic.lifecycle.SshBasedAppSetup;
import brooklyn.entity.messaging.JMSBroker
import brooklyn.entity.messaging.JMSDestination
import brooklyn.entity.messaging.Queue
import brooklyn.entity.messaging.Topic
import brooklyn.entity.messaging.activemq.ActiveMQQueue;
import brooklyn.entity.messaging.activemq.ActiveMQTopic;
import brooklyn.event.adapter.SensorRegistry
import brooklyn.event.adapter.legacy.OldJmxSensorAdapter;
import brooklyn.event.adapter.legacy.ValueProvider;
import brooklyn.event.basic.ConfiguredAttributeSensor
import brooklyn.location.basic.SshMachineLocation

/**
 * An {@link brooklyn.entity.Entity} that represents a single Qpid broker instance.
 */
public class QpidBroker extends JMSBroker<QpidQueue, QpidTopic> {
    private static final Logger log = LoggerFactory.getLogger(QpidBroker.class)

    public static final ConfiguredAttributeSensor<Integer> AMQP_PORT = Attributes.AMQP_PORT
    public static final ConfiguredAttributeSensor<String> VIRTUAL_HOST_NAME = [String, "qpid.virtualHost", "Qpid virtual host name", "localhost" ]

    String virtualHost

    public QpidBroker(Map properties=[:], Entity owner=null) {
        super(properties, owner)

        virtualHost = properties.virtualHost ?: getConfig(VIRTUAL_HOST_NAME.configKey)
        setAttribute(VIRTUAL_HOST_NAME, virtualHost)

        setConfigIfValNonNull(Attributes.AMQP_PORT.configKey, properties.amqpPort)

        setConfigIfValNonNull(Attributes.JMX_USER.configKey, properties.user ?: "admin")
        setConfigIfValNonNull(Attributes.JMX_PASSWORD.configKey, properties.password ?: "admin")
    }

    public void setBrokerUrl() {
        String urlFormat = "amqp://guest:guest@monterey/%s?brokerlist='tcp://%s:%d'"
        setAttribute(BROKER_URL, String.format(urlFormat, getAttribute(VIRTUAL_HOST_NAME), getAttribute(HOSTNAME), getAttribute(AMQP_PORT)))
    }

    public QpidQueue createQueue(Map properties) {
        return new QpidQueue(properties);
    }

    public QpidTopic createTopic(Map properties) {
        return new QpidTopic(properties);
    }

    public SshBasedAppSetup getSshBasedSetup(SshMachineLocation machine) {
        return QpidSetup.newInstance(this, machine)
    }

    @Override
    public void addJmxSensors() {
        sensorRegistry.addSensor(JavaApp.SERVICE_UP, { computeNodeUp() } as ValueProvider)

		setAttribute(Attributes.JMX_USER)
		setAttribute(Attributes.JMX_PASSWORD)
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

public abstract class QpidDestination extends JMSDestination {
    String virtualHost

    protected ObjectName virtualHostManager
    protected ObjectName exchange

    public QpidDestination(Map properties=[:], Entity owner=null) {
        super(properties, owner)
    }

    public void init() {
        virtualHost = properties.virtualHost ?: getConfig(QpidBroker.VIRTUAL_HOST_NAME.configKey)
        setAttribute(QpidBroker.VIRTUAL_HOST_NAME, virtualHost)
        virtualHostManager = new ObjectName("org.apache.qpid:type=VirtualHost.VirtualHostManager,VirtualHost=\"${virtualHost}\"")
    }

    public void create() {
        jmxAdapter.operation(virtualHostManager, "createNewQueue", name, getOwner().getAttribute(Attributes.JMX_USER), true)
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
		sensorRegistry.close()
        super.destroy()
	}

    @Override
    public Collection<String> toStringFieldsToInclude() {
        return super.toStringFieldsToInclude() + ['name']
    }
}

public class QpidQueue extends QpidDestination implements Queue {
    public QpidQueue(Map properties=[:], Entity owner=null) {
        super(properties, owner)
    }

    @Override
    public void init() {
        setAttribute QUEUE_NAME, name
        super.init()
        exchange = new ObjectName("org.apache.qpid:type=VirtualHost.Exchange,VirtualHost=\"${virtualHost}\",name=\"amq.direct\",ExchangeType=direct")
    }

    public void addJmxSensors() {
        String queue = "org.apache.qpid:type=VirtualHost.Queue,VirtualHost=\"${virtualHost}\",name=\"${name}\""
        sensorRegistry.addSensor(QUEUE_DEPTH_BYTES, jmxAdapter.newAttributeProvider(queue, "QueueDepth"))
        sensorRegistry.addSensor(QUEUE_DEPTH_MESSAGES, jmxAdapter.newAttributeProvider(queue, "MessageCount"))
    }

    public void removeJmxSensors() {
        sensorRegistry.removeSensor(QUEUE_DEPTH_BYTES)
        sensorRegistry.removeSensor(QUEUE_DEPTH_MESSAGES)
    }
}

public class QpidTopic extends QpidDestination implements Topic {
    public QpidTopic(Map properties=[:], Entity owner=null) {
        super(properties, owner)
    }

    @Override
    public void init() {
        setAttribute TOPIC_NAME, name
        super.init()
        exchange = new ObjectName("org.apache.qpid:type=VirtualHost.Exchange,VirtualHost=\"${virtualHost}\",name=\"amq.topic\",ExchangeType=topic")
    }

    public void addJmxSensors() {
        //TODO add sensors for topic
    }

    public void removeJmxSensors() {
    }
}
