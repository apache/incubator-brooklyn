package brooklyn.entity.messaging.qpid

import java.util.Collection
import java.util.Map

import javax.management.ObjectName

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Entity
import brooklyn.entity.basic.Attributes
import brooklyn.entity.basic.SoftwareProcessEntity
import brooklyn.entity.basic.UsesJmx
import brooklyn.entity.messaging.Queue
import brooklyn.entity.messaging.Topic
import brooklyn.entity.messaging.amqp.AmqpExchange
import brooklyn.entity.messaging.amqp.AmqpServer;
import brooklyn.entity.messaging.jms.JMSBroker;
import brooklyn.entity.messaging.jms.JMSDestination;
import brooklyn.event.adapter.JmxHelper
import brooklyn.event.adapter.JmxSensorAdapter
import brooklyn.event.adapter.SensorRegistry
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey
import brooklyn.event.basic.BasicConfigKey
import brooklyn.event.basic.PortAttributeSensorAndConfigKey;
import brooklyn.location.PortRange
import brooklyn.location.basic.SshMachineLocation
import brooklyn.util.flags.SetFromFlag

/**
 * An {@link brooklyn.entity.Entity} that represents a single Qpid broker instance, using AMQP 0-10.
 */
public class QpidBroker extends JMSBroker<QpidQueue, QpidTopic> implements UsesJmx, AmqpServer {
    private static final Logger log = LoggerFactory.getLogger(QpidBroker.class)

    /* Qpid runtime file locations for convenience. */

    public static final String CONFIG_XML = "etc/config.xml"
    public static final String VIRTUALHOSTS_XML = "etc/virtualhosts.xml"
    public static final String PASSWD = "etc/passwd"

    @SetFromFlag("version")
    public static final BasicConfigKey<String> SUGGESTED_VERSION = [ SoftwareProcessEntity.SUGGESTED_VERSION, "0.14" ]
    
    @SetFromFlag("amqpPort")
    public static final PortAttributeSensorAndConfigKey AMQP_PORT = AmqpServer.AMQP_PORT

    @SetFromFlag("virtualHost")
    public static final BasicAttributeSensorAndConfigKey<String> VIRTUAL_HOST_NAME = AmqpServer.VIRTUAL_HOST_NAME

    @SetFromFlag("amqpVersion")
    public static final BasicAttributeSensorAndConfigKey<String> AMQP_VERSION = [ AmqpServer.AMQP_VERSION, AmqpServer.AMQP_0_10 ]

    /** Files to be copied to the server, map of "subpath/file.name": "classpath://foo/file.txt" (or other url) */
    @SetFromFlag("runtimeFiles")
    public static final BasicConfigKey<Map> RUNTIME_FILES = [ Map, "qpid.files.runtime", "Map of files to be copied, keyed by destination name relative to runDir" ]

    //TODO if this is included, AbstractEntity complains about multiple sensors;
//    //should be smart enough to exclude;
//    //also, we'd prefer to hide this from being configurable full stop
//    /** not configurable; must be 100 more than JMX port */
//    public static final PortAttributeSensorAndConfigKey RMI_PORT = [ UsesJmx.RMI_PORT, 9101 ] 
    
    public String getVirtualHost() { return getAttribute(VIRTUAL_HOST_NAME) }
    public String getAmqpVersion() { return getAttribute(AMQP_VERSION) }
    public Integer getAmqpPort() { return getAttribute(AMQP_PORT) }

    public QpidBroker(Map properties=[:], Entity owner=null) {
        super(properties, owner)

        // TODO test, then change keys to be jmxUser, jmxPassword, configurable on the keys themselves
        setConfigIfValNonNull(Attributes.JMX_USER, properties.user ?: "admin")
        setConfigIfValNonNull(Attributes.JMX_PASSWORD, properties.password ?: "admin")
    }

    public void setBrokerUrl() {
        String urlFormat = "amqp://guest:guest@/%s?brokerlist='tcp://%s:%d'"
        setAttribute(BROKER_URL, String.format(urlFormat, getAttribute(VIRTUAL_HOST_NAME), getAttribute(HOSTNAME), getAttribute(AMQP_PORT)))
    }

    public QpidQueue createQueue(Map properties) {
        return new QpidQueue(properties, this)
    }

    public QpidTopic createTopic(Map properties) {
        return new QpidTopic(properties, this)
    }

    public QpidSshDriver newDriver(SshMachineLocation machine) {
        return new QpidSshDriver(this, machine)
    }

    @Override
    protected Collection<Integer> getRequiredOpenPorts() {
        Set<Integer> ports = super.getRequiredOpenPorts() + getAttribute(AMQP_PORT)
        Integer jmx = getAttribute(JMX_PORT)
        if (jmx) ports += (jmx + 100)
        log.debug("getRequiredOpenPorts detected expanded (qpid) ports ${ports} for ${this}")
        ports
    }

    @Override
    protected void preStart() {
        super.preStart();
        // NOTE difference of 100 hard-coded in Qpid - RMI port ignored
        setAttribute(RMI_PORT, getAttribute(JMX_PORT) + 100)
    }

    transient JmxSensorAdapter jmxAdapter;

    @Override
    protected void connectSensors() {
        jmxAdapter = sensorRegistry.register(new JmxSensorAdapter())
        jmxAdapter.objectName("org.apache.qpid:type=ServerInformation,name=ServerInformation")
            .attribute("ProductVersion")
            .subscribe(SERVICE_UP) {
                if (it == null) return false
                if (it == getConfig(SUGGESTED_VERSION)) return true
                log.warn("ProductVersion is ${it}, requested version is {}", getConfig(SUGGESTED_VERSION))
                return false
            }
        jmxAdapter.activateAdapter()
        
		setAttribute(Attributes.JMX_USER)
		setAttribute(Attributes.JMX_PASSWORD)
    }

    @Override
    public Collection<String> toStringFieldsToInclude() {
        return super.toStringFieldsToInclude() + [ 'amqpPort' ]
    }

}

public abstract class QpidDestination extends JMSDestination implements AmqpExchange {
    public static final Logger log = LoggerFactory.getLogger(QpidDestination.class);
    
    @SetFromFlag
    String virtualHost

    protected ObjectName virtualHostManager
    protected ObjectName exchange
    protected transient SensorRegistry sensorRegistry
    protected transient JmxSensorAdapter jmxAdapter

    public QpidDestination(Map properties=[:], Entity owner=null) {
        super(properties, owner)
    }

    public void init() {
        if (!virtualHost) virtualHost = getConfig(QpidBroker.VIRTUAL_HOST_NAME)
        setAttribute(QpidBroker.VIRTUAL_HOST_NAME, virtualHost)
        virtualHostManager = new ObjectName("org.apache.qpid:type=VirtualHost.VirtualHostManager,VirtualHost=\"${virtualHost}\"")
        if (!sensorRegistry) sensorRegistry = new SensorRegistry(this)
        def helper = new JmxHelper(owner)
        helper.connect();
        jmxAdapter = sensorRegistry.register(new JmxSensorAdapter(helper));
    }

    public void create() {
        jmxAdapter.helper.operation(virtualHostManager, "createNewQueue", name, getOwner().getAttribute(Attributes.JMX_USER), true)
        jmxAdapter.helper.operation(exchange, "createNewBinding", name, name)
        connectSensors()
        sensorRegistry.activateAdapters()
    }
    
    public void delete() {
        jmxAdapter.helper.operation(exchange, "removeBinding", name, name)
        jmxAdapter.helper.operation(virtualHostManager, "deleteQueue", name)
        sensorRegistry.deactivateAdapters()
    }

    /**
     * Return the AMQP name for the queue.
     */
    public String getQueueName() {
        if (getOwner().amqpVersion == AmqpServer.AMQP_0_10) {
	        return String.format("'%s'/'%s'; { assert: never }", exchangeName, name)
        } else {
	        return name
        }
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
        exchange = new ObjectName("org.apache.qpid:type=VirtualHost.Exchange,VirtualHost=\"${virtualHost}\",name=\"${exchangeName}\",ExchangeType=direct")
    }

    public void connectSensors() {
        String queue = "org.apache.qpid:type=VirtualHost.Queue,VirtualHost=\"${virtualHost}\",name=\"${name}\""
        jmxAdapter.objectName(queue).with {
            attribute("QueueDepth").poll(QUEUE_DEPTH_BYTES)
            attribute("MessageCount").poll(QUEUE_DEPTH_MESSAGES)
        }
    }

    /** {@inheritDoc} */
    public String getExchangeName() { AmqpExchange.DIRECT }
}

public class QpidTopic extends QpidDestination implements Topic {
    public QpidTopic(Map properties=[:], Entity owner=null) {
        super(properties, owner)
    }

    // TODO sensors
    public void connectSensors() { }
    
    @Override
    public void init() {
        setAttribute TOPIC_NAME, name
        super.init()
        exchange = new ObjectName("org.apache.qpid:type=VirtualHost.Exchange,VirtualHost=\"${virtualHost}\",name=\"${exchangeName}\",ExchangeType=topic")
    }

    /** {@inheritDoc} */
    public String getExchangeName() { AmqpExchange.TOPIC }

    public String getTopicName() { queueName }
}
