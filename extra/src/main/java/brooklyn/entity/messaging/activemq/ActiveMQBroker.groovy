package brooklyn.entity.messaging.activemq

import java.util.Collection
import java.util.Map
import java.util.concurrent.TimeUnit;

import javax.management.InstanceNotFoundException
import javax.management.ObjectName
import javax.management.RuntimeMBeanException;

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import com.google.common.base.Throwables;

import brooklyn.entity.Entity
import brooklyn.entity.basic.Attributes
import brooklyn.entity.basic.legacy.JavaApp;
import brooklyn.entity.basic.lifecycle.legacy.SshBasedAppSetup;
import brooklyn.entity.messaging.JMSBroker
import brooklyn.entity.messaging.JMSDestination
import brooklyn.entity.messaging.Queue
import brooklyn.entity.messaging.Topic
import brooklyn.event.adapter.SensorRegistry
import brooklyn.event.adapter.legacy.OldJmxSensorAdapter;
import brooklyn.event.adapter.legacy.ValueProvider;
import brooklyn.event.basic.ConfiguredAttributeSensor
import brooklyn.location.basic.SshMachineLocation
import brooklyn.util.internal.Repeater;

/**
 * An {@link brooklyn.entity.Entity} that represents a single ActiveMQ broker instance.
 */
public class ActiveMQBroker extends JMSBroker<ActiveMQQueue, ActiveMQTopic> {
	private static final Logger log = LoggerFactory.getLogger(ActiveMQBroker.class)

	public static final ConfiguredAttributeSensor<Integer> OPEN_WIRE_PORT = [
		Integer,
		"openwire.port",
		"OpenWire port",
		61616
	]

	public ActiveMQBroker(Map properties=[:], Entity owner=null) {
		super(properties, owner)

		setConfigIfValNonNull(OPEN_WIRE_PORT.configKey, properties.openWirePort)

		setConfigIfValNonNull(Attributes.JMX_USER.configKey, properties.user ?: "admin")
		setConfigIfValNonNull(Attributes.JMX_PASSWORD.configKey, properties.password ?: "activemq")
	}

	@Override
	protected Collection<Integer> getRequiredOpenPorts() {
		Collection<Integer> result = super.getRequiredOpenPorts()
		result.add(getConfig(OPEN_WIRE_PORT.configKey))
		result.add(getConfig(RMI_PORT.configKey))
		return result
	}

	public void setBrokerUrl() {
		setAttribute(BROKER_URL, String.format("tcp://%s:%d", getAttribute(HOSTNAME), getAttribute(OPEN_WIRE_PORT)))
	}
	
	public ActiveMQQueue createQueue(Map properties) {
		return new ActiveMQQueue(properties);
	}

	public ActiveMQTopic createTopic(Map properties) {
		return new ActiveMQTopic(properties);
	}

	public SshBasedAppSetup newDriver(SshMachineLocation machine) {
		return ActiveMQSetup.newInstance(this, machine)
	}

	@Override
	public void addJmxSensors() {
		sensorRegistry.addSensor(JavaApp.SERVICE_UP, {
			computeNodeUp() } as ValueProvider)
	}

	@Override
	public Collection<String> toStringFieldsToInclude() {
		return super.toStringFieldsToInclude() + ['openWirePort']
	}

	public void waitForServiceUp() {
		Repeater.create(timeout: 60*TimeUnit.SECONDS)
			.rethrowException().repeat().until { computeNodeUp() }
//		long expiry = System.currentTimeMillis() + 60*1000;
//		Exception lastError = null;
//		while (false) {
//			try {
//				if (computeNodeUp()) break;
//			} catch (RuntimeMBeanException e) {
//				//ignore while starting, report if we can't start
//				lastError = e;
//			} 
//			if (System.currentTimeMillis()>expiry)
//				throw new IllegalStateException("failed to start", lastError) 
//			Thread.sleep(200);
//		}
		log.info("started JMS $this")
	}

	protected boolean computeNodeUp() {
		String host = getAttribute(HOSTNAME)

		//        ValueProvider<String> provider = jmxAdapter.newAttributeProvider("org.apache.camel:context=*/camel,type=components,name=\"activemq\"", "State")
		//        		FIXME ADK i (alex) am not seeing any camel attributes ?

		//FIXME ADK - also i (alex) wonder if we can remove the "BrokerName=localhost" part 
		//of the object name lookups, in favour of = *
		//(we can be pretty sure there is only one being hosted by this process, right?)

		//caller catches most errors, but logs them; this particular error
		try {
			ValueProvider<String> provider = jmxAdapter.newAttributeProvider("org.apache.activemq:BrokerName=localhost,Type=Broker", "BrokerId")
			String state = provider.compute()
			return (state)  //was =="Started" for camel
		} catch (RuntimeMBeanException e) {
            if (e.cause in NullPointerException) {
                log.warn("ActiveMQ gave NullPointerException when reading JMX computeNodeUp; known issue with ActiveMQ and should resovle itself soon after start-up")
            } else {
                Throwables.propagate(e);
            }
        }
//		} catch (Exception e) {
//			//get InstanceNotFound and even NPE (from looking up broker id on other side)
//			//if connect too early
//			return false
//		}
	}
}

public abstract class ActiveMQDestination extends JMSDestination {
	protected ObjectName broker

	public ActiveMQDestination(Map properties=[:], Entity owner=null) {
		super(properties, owner)
	}

	public void init() {
		broker = new ObjectName("org.apache.activemq:BrokerName=localhost,Type=Broker")
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
		println "id is "+jmxAdapter.getAttribute(broker, "BrokerId")
		jmxAdapter.operation(broker, "addQueue", name)
		addJmxSensors()
	}

	public void delete() {
		jmxAdapter.operation(broker, "removeQueue", name)
		removeJmxSensors()
	}

	public void addJmxSensors() {
		String queue = "org.apache.activemq:BrokerName=localhost,Type=Queue,Destination=${name}"
		sensorRegistry.addSensor(QUEUE_DEPTH_MESSAGES, jmxAdapter.newAttributeProvider(queue, "QueueSize"))
	}

	public void removeJmxSensors() {
		sensorRegistry.removeSensor(QUEUE_DEPTH_MESSAGES)
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
