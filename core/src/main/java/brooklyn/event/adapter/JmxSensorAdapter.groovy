package brooklyn.event.adapter

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

import javax.management.AttributeList
import javax.management.MBeanInfo
import javax.management.MBeanServerConnection
import javax.management.ObjectInstance
import javax.management.ObjectName
import javax.management.remote.JMXConnector
import javax.management.remote.JMXConnectorFactory
import javax.management.remote.JMXServiceURL

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Entity
import brooklyn.event.Sensor
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.event.basic.JmxAttributeSensor
import brooklyn.event.basic.SensorEvent

/**
 * This class adapts JMX {@link ObjectName} dfata to {@link Sensor} data for a particular {@link Entity}, updating the
 * {@link Activity} as required.
 * 
 *  The adapter normally polls the JMX server every second to update sensors, which could involve aggregation of data
 *  or simply reading values and setting them in the attribute map of the activity model.
 */
public class JmxSensorAdapter implements SensorAdapter {
    static final Logger log = LoggerFactory.getLogger(JmxSensorAdapter.class);
 
    final Entity entity
	final String jmxUrl
    final Map<?, ?> properties  = [
            period : 500,
            connectDelay : 1000
        ]   
 
    ScheduledExecutorService exec = Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors())
	JMXConnector jmxc
	MBeanServerConnection mbsc = null
    ScheduledFuture monitor = null
    Map<String, JmxAttributeSensor<?>> sensors = [:]
    Map<String, ObjectName> objects = [:]
    Map<String, BasicAttributeSensor<?>> calculated = [:]
    Map<String, ScheduledFuture> scheduled = [:]
    
    /* Default polling interval, milliseconds */
	long defaultPollingPeriod = 500;
    
    public JmxSensorAdapter(Entity entity, long timeout = -1, Map properties = [:]) {
        this.entity = entity
        this.properties << properties
 
        String host = entity.attributes['jmxHost']
        int port = entity.attributes['jmxPort']
 
        this.jmxUrl =  "service:jmx:rmi:///jndi/rmi://"+host+":"+port+"/jmxrmi";
        
        if (!connect(timeout)) throw new IllegalStateException("Could not connect to JMX service")
    }
    
    public void addSensor(BasicAttributeSensor sensor, String objectName, Closure calculate, long period) {
        log.info "adding calculated sensor {} with delay {}", sensor.name, period
        calculated[sensor.getName()] = sensor
        objects[sensor.getName()] = new ObjectName(objectName)
        entity.updateAttribute(sensor, null)
        
        scheduled[sensor.getName()] = exec.scheduleWithFixedDelay(calculate, 0L, period, TimeUnit.MILLISECONDS)
    }
 
    public void addSensor(JmxAttributeSensor sensor) {
        log.info "adding sensor {} for {} - {}", sensor.name, sensor.objectName, sensor.attribute
        sensors[sensor.getName()] = sensor
        objects[sensor.getName()] = new ObjectName(sensor.objectName)
        entity.updateAttribute(sensor, null)
    }
    
    public <T> void addSensor(Sensor<T> sensor, String objectName) {
        sensors.put(sensor.getName(), sensor);
    }
 
    public <T> void addSensor(Sensor<T> sensor, ObjectName objectName) {
        addSensor(sensor, objectname.getCanonicalName())
    }

	public boolean isConnected() {
		return (jmxc && mbsc);
	}
 
	/** attempts to connect immediately */
	public void connect() throws IOException {
		if (jmxc) jmxc.close()
		JMXServiceURL url = new JMXServiceURL(jmxUrl)
		jmxc = JMXConnectorFactory.connect(url, null);
		mbsc = jmxc.getMBeanServerConnection();
 
        monitor = exec.scheduleWithFixedDelay({ updateJmxSensors() }, properties['period'], properties['period'], TimeUnit.MILLISECONDS)
	}
 
	/** continuously attempts to connect (blocking), for at least the indicated amount of time; or indefinitely if -1 */
	public boolean connect(long timeoutMillis) {
		println "invoking connect to "+jmxUrl
		long thisStartTime = System.currentTimeMillis()
		long endTime = thisStartTime + timeoutMillis
		if (timeoutMillis==-1) endTime = Long.MAX_VALUE
		while (thisStartTime <= endTime) {
			thisStartTime = System.currentTimeMillis()
			println "$thisStartTime trying connection to "+jmxUrl
			try {
				connect()
				return true
			} catch (IOException e) {
				println ""+System.currentTimeMillis()+" failed connection to "+jmxUrl+" ("+e+")"
			}
			Thread.sleep properties['connectDelay']
		}
		false
	}
	
	public void disconnect() {
		if (jmxc) {
			jmxc.close()
			jmxc = null
			mbsc = null
		}
        if (monitor) monitor.cancel(true) 
        scheduled.each { key, ScheduledFuture future -> future.cancel(true) }
	}
	
	public void checkConnected() {
		if (!isConnected()) throw new IllegalStateException("JmxTool must be connected")
	}
	
	/** returns all attributes on a specific named object */
	public Map getAttributes(String objectName) {
		checkConnected()
		ObjectName mxbeanName = new ObjectName(objectName);
		ObjectInstance bean = mbsc.getObjectInstance(mxbeanName)
		Map r = [:]
		MBeanInfo info = mbsc.getMBeanInfo(bean.getObjectName())
		info.getAttributes().each { r[it.getName()] = null }
		AttributeList attrs = mbsc.getAttributes bean.getObjectName(), r.keySet() as String[]
		attrs.asList().each { r[it.getName()] = it.getValue(); }
		r
	}
	
	/**
	 * Returns all attributes for all children matching a given objectName string.
	 * 
	 * TODO allow overrides for attributes which should take min, max, rather than sum
	 * 
	 * <pre>
	 *  children {
	 *    MBean1[objName] {
	 *      attributes {
	 *        size: 1
	 *      }
	 *      notifications {
	 *        desiredSize {
	 *          value: 4
	 *          seqNo: 8
	 *          oldValue: 1
	 *        }
	 *      }
	 *    }
	 *    MBean2[objName2] {
	 *      attributes {
	 *        size: 0
	 *      }
	 *  }
	 *  totals {
	 *    size: 1
	 *  }
	 * </pre>
	 */
	public Map getChildrenAttributesWithTotal(String objectName) {
		checkConnected()
		ObjectName mxbeanName = new ObjectName(objectName);
		Set<ObjectInstance> matchingBeans = mbsc.queryMBeans mxbeanName, null
		Map r = [:]
		r.totals = [:]
		matchingBeans.each {
			ObjectInstance bean = it
			if (!r.children) r.children=[:]
			def c = r.children[it.toString()] = [:]
			MBeanInfo info = mbsc.getMBeanInfo(it.getObjectName())
			c.attributes = [:]
			info.getAttributes().each {
				c.attributes[it.getName()] = null
			}
			AttributeList attrs = mbsc.getAttributes it.getObjectName(), c.attributes.keySet() as String[]
			attrs.asList().each {
				c.attributes[it.getName()] = it.getValue();
				if (it.getValue() in Number)
					r.totals[it.getName()] = (r.totals[it.getName()]?:0) + it.getValue()
			}
//			info.getNotifications().each { println "  notf $it" }
		}
		r
	}

    public void updateJmxSensors() {
        sensors.keySet() each { s ->
                JmxAttributeSensor<?> sensor = sensors.get(s)   
		        def data = getAttributes(sensor.objectName)
                log.info "data for {},{} was {}", sensor.name, sensor.objectName, data[sensor.attribute]
                def newValue = data[sensor.attribute]
                entity.updateAttribute(sensor, newValue)
	        } 
    }
    
    public <T> void subscribe(String sensorName) {
        Sensor<?> sensor = sensors.get(sensorName) ?: calculated.get(sensorName) ?: null
        if (sensor == null) throw new IllegalStateException("Sensor $sensorName not found");
        subscribe(sensor)
    }
 
    public <T> void subscribe(final Sensor<T> sensor) {
        subscriptions += sensor
    }
    
    public <T> T poll(String sensorName) {
        Sensor<?> sensor = sensors.get(sensorName) ?: calculated.get(sensorName) ?: null
        if (sensor == null) throw new IllegalStateException("Sensor $sensorName not found");
        poll(sensor)
    }
 
    public <T> T poll(Sensor<T> sensor) {
        def value = entity.attributes[sensorName]
        SensorEvent<?> event = new SensorEvent(sensor, entity, value)
        entity.raiseEvent event
        value
    }
}