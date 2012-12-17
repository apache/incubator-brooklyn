package brooklyn.entity.messaging.activemq

import javax.management.ObjectName

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Entity
import brooklyn.entity.basic.Attributes
import brooklyn.entity.basic.Entities
import brooklyn.entity.basic.SoftwareProcessEntity
import brooklyn.entity.java.UsesJmx
import brooklyn.entity.messaging.Queue
import brooklyn.entity.messaging.Topic
import brooklyn.entity.messaging.jms.JMSBroker
import brooklyn.entity.messaging.jms.JMSDestination
import brooklyn.event.adapter.JmxHelper
import brooklyn.event.adapter.JmxSensorAdapter
import brooklyn.event.adapter.SensorRegistry
import brooklyn.event.basic.AttributeSensorAndConfigKey
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey
import brooklyn.event.basic.BasicConfigKey
import brooklyn.event.basic.PortAttributeSensorAndConfigKey
import brooklyn.util.flags.SetFromFlag

import com.google.common.base.Objects.ToStringHelper
/**
 * An {@link brooklyn.entity.Entity} that represents a single ActiveMQ broker instance.
 */
public class ActiveMQBroker extends JMSBroker<ActiveMQQueue, ActiveMQTopic> implements UsesJmx {
	private static final Logger log = LoggerFactory.getLogger(ActiveMQBroker.class)

    @SetFromFlag("version")
    public static final BasicConfigKey<String> SUGGESTED_VERSION = [ SoftwareProcessEntity.SUGGESTED_VERSION, "5.7.0" ]

    /** download mirror, if desired */
    @SetFromFlag("mirrorUrl")
    public static final BasicConfigKey<String> MIRROR_URL = [ String, "activemq.install.mirror.url", "URL of mirror",
        "http://www.mirrorservice.org/sites/ftp.apache.org/activemq/apache-activemq"
         ]

    @SetFromFlag("tgzUrl")
    public static final BasicConfigKey<String> TGZ_URL = [ String, "activemq.install.tgzUrl", "URL of TGZ download file", null ]

    @SetFromFlag("openWirePort")
	public static final PortAttributeSensorAndConfigKey OPEN_WIRE_PORT = [ "openwire.port", "OpenWire port", "61616+" ]

    @SetFromFlag("jmxUser")
    public static final BasicAttributeSensorAndConfigKey<String> JMX_USER = new BasicAttributeSensorAndConfigKey<String>(Attributes.JMX_USER, "admin");
    
    @SetFromFlag("jmxPassword")
    public static final BasicAttributeSensorAndConfigKey<String> JMX_PASSWORD = new BasicAttributeSensorAndConfigKey<String>(Attributes.JMX_PASSWORD, "admin");

	public ActiveMQBroker(Map properties=[:], Entity owner=null) {
		super(properties, owner)
	}

	public void setBrokerUrl() {
		setAttribute(BROKER_URL, String.format("tcp://%s:%d", getAttribute(HOSTNAME), getAttribute(OPEN_WIRE_PORT)))
	}
	
	public ActiveMQQueue createQueue(Map properties) {
		ActiveMQQueue result = new ActiveMQQueue(properties);
        Entities.manage(result);
        result.init();
        result.create();
        return result;
	}

	public ActiveMQTopic createTopic(Map properties) {
		ActiveMQTopic result = new ActiveMQTopic(properties);
        Entities.manage(result);
        result.init();
        result.create();
        return result;
	}

    @Override     
    protected void connectSensors() {
        setAttribute(BROKER_URL, String.format("tcp://%s:%d", getAttribute(HOSTNAME), getAttribute(OPEN_WIRE_PORT)))
        
        JmxSensorAdapter jmxAdapter = sensorRegistry.register(new JmxSensorAdapter());
        jmxAdapter.objectName("org.apache.activemq:BrokerName=localhost,Type=Broker")
                .attribute("BrokerId")
                .subscribe(SERVICE_UP) { it as Boolean }
    }

	@Override
    protected ToStringHelper toStringHelper() {
        return super.toStringHelper().add("openWirePort", getAttribute(OPEN_WIRE_PORT));
    }

    @Override
    public Class getDriverInterface() {
        return ActiveMQDriver.class;
    }
}

public abstract class ActiveMQDestination extends JMSDestination {
	protected ObjectName broker
	protected transient SensorRegistry sensorRegistry
	protected transient JmxSensorAdapter jmxAdapter

	public ActiveMQDestination(Map properties=[:], Entity owner=null) {
		super(properties, owner)
	}
    
	public void init() {
        //assume just one BrokerName at this endpoint
		broker = new ObjectName("org.apache.activemq:BrokerName=localhost,Type=Broker")
        if (!sensorRegistry) sensorRegistry = new SensorRegistry(this)
        def helper = new JmxHelper(owner)
        helper.connect();
        jmxAdapter = sensorRegistry.register(new JmxSensorAdapter(helper));
	}
}

public class ActiveMQQueue extends ActiveMQDestination implements Queue {
    public static final Logger log = LoggerFactory.getLogger(ActiveMQQueue.class);
            
	public ActiveMQQueue(Map properties=[:], Entity owner=null) {
		super(properties, owner)
	}

	@Override
	public void init() {
		setAttribute QUEUE_NAME, name
		super.init()
	}

	public void create() {
		if (log.isDebugEnabled()) log.debug("${this} adding queue ${name} to broker "+jmxAdapter.helper.getAttribute(broker, "BrokerId"))
        
		jmxAdapter.helper.operation(broker, "addQueue", name)
        
        connectSensors();
	}

	public void delete() {
		jmxAdapter.helper.operation(broker, "removeQueue", name)
        sensorRegistry.deactivateAdapters()
	}

    @Override
    public void connectSensors() {
        String queue = "org.apache.activemq:BrokerName=localhost,Type=Queue,Destination=${name}"
        jmxAdapter.objectName(queue).attribute("QueueSize").subscribe(QUEUE_DEPTH_MESSAGES)
    }

    public String getQueueName() { name }

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

    @Override
	public void create() {
		jmxAdapter.helper.operation(broker, "addTopic", name)
		connectSensors()
	}

	public void delete() {
		jmxAdapter.helper.operation(broker, "removeTopic", name)
		sensorRegistry.deactivateAdapters()
	}

	public void connectSensors() {
		//TODO add sensors for topics
	}

    public String getTopicName() { name }
}
