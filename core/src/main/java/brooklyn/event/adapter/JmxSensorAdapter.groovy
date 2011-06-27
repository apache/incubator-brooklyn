package brooklyn.event.adapter

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

import javax.management.Attribute
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

import brooklyn.entity.basic.EntityLocal
import brooklyn.event.AttributeSensor
import brooklyn.event.Sensor
import brooklyn.event.basic.BasicAttributeSensor

/**
 * This class adapts JMX {@link ObjectName} dfata to {@link Sensor} data for a particular {@link Entity}, updating the
 * {@link Activity} as required.
 * 
 *  The adapter normally polls the JMX server every second to update sensors, which could involve aggregation of data
 *  or simply reading values and setting them in the attribute map of the activity model.
 */
public class JmxSensorAdapter implements  SensorAdapter {
    static final Logger log = LoggerFactory.getLogger(JmxSensorAdapter.class);
 
    final EntityLocal entity
	final String jmxUrl
    final Map<?, ?> properties  = [
            period : 500,
            connectDelay : 1000
        ]   
 
    ScheduledExecutorService exec = Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors())
 
	JMXConnector jmxc
	MBeanServerConnection mbsc = null
    ScheduledFuture monitor = null
 
    Map<String, AttributeSensor<?>> sensors = [:]
    Map<String, JmxValueProvider<?>> providers = [:]
    Map<String, BasicAttributeSensor<?>> calculated = [:]
    Map<String, ScheduledFuture> scheduled = [:]
    
    /* Default polling interval, milliseconds */
	long defaultPollingPeriod = 500;
    
    public JmxSensorAdapter(EntityLocal entity, long timeout = -1, Map properties = [:]) {
        this.entity = entity
        this.properties << properties
 
        String host = entity.attributes['jmxHost']
        int port = entity.attributes['jmxPort']
 
        this.jmxUrl =  "service:jmx:rmi:///jndi/rmi://"+host+":"+port+"/jmxrmi";
        
        if (!connect(timeout)) throw new IllegalStateException("Could not connect to JMX service")
    }

    public <T> void addSensor(BasicAttributeSensor<T> sensor, Closure calculate, long period) {
        log.debug "adding calculated sensor {} with delay {}", sensor.name, period
        calculated[sensor.getName()] = sensor
        entity.updateAttribute(sensor, null)

        Closure safeCalculate = {
            try {
                calculate.call()
            } catch (Exception e) {
                log.error "Error calculating value for sensor $sensor on entity $entity", e
            }
        }

        scheduled[sensor.getName()] = exec.scheduleWithFixedDelay(safeCalculate, 0L, period, TimeUnit.MILLISECONDS)
    }
 
    public <T> void addSensor(BasicAttributeSensor<T> sensor, String objectName, String attribute) {
        JmxValueProvider<T> provider = new JmxValueProvider(objectName, attribute)
        log.debug "adding sensor {} for {} - {}", sensor.name, provider.objectName, provider.attribute
        sensors[sensor.getName()] = sensor
        providers[sensor.getName()] = provider
        
        try {
	        entity.getAttribute(sensor)
        } catch (NullPointerException npe) {
            entity.updateAttribute(sensor, null)
        }
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
		log.debug "invoking connect to {}", jmxUrl
		long thisStartTime = System.currentTimeMillis()
		long endTime = thisStartTime + timeoutMillis
		if (timeoutMillis==-1) endTime = Long.MAX_VALUE
		while (thisStartTime <= endTime) {
			thisStartTime = System.currentTimeMillis()
			log.debug "{} trying connection to {}", thisStartTime, jmxUrl
			try {
				connect()
				return true
			} catch (IOException e) {
				log.error "{} failed connection to {} ({})", System.currentTimeMillis(), jmxUrl, e.message
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

	/**
	 * Returns all attributes on a specific named object
	 */
	public Map getAttributes(String name) {
		checkConnected()
		ObjectName objectName = new ObjectName(name);
        Set<ObjectInstance> beans = mbsc.queryMBeans(objectName, null)
        ObjectInstance bean = beans.iterator().next();
        // Use 'query' because objectName could contain wildcards
        // TODO What if more than one bean?
		MBeanInfo info = mbsc.getMBeanInfo(bean.getObjectName())
		Map r = [:]
		info.getAttributes().each { r[it.getName()] = null }
		AttributeList list = mbsc.getAttributes bean.getObjectName(), r.keySet() as String[]
		list.each { r[it.getName()] = it.getValue(); }
        log.trace "returning attributes: {}", r
		r
	}

    /**
     * Returns a specific attribute for a JMX {@link Objectname}.
     */
    public Map getAttribute(ObjectName objectName, String attribute) {
        ObjectInstance bean = mbsc.getObjectInstance(objectName)
        MBeanInfo info = mbsc.getMBeanInfo(bean.getObjectName())
        AttributeList list = mbsc.getAttributes bean.getObjectName(), [ attribute ]
        Attribute result = list.find { Attribute a -> a.name.equals(attribute) }
        result.value
    }

    private void updateJmxSensors() {
        sensors.keySet() each { s ->
                AttributeSensor<?> sensor = sensors.get(s)
                JmxValueProvider<?> provider = providers.get(s)
		        def newValue = getAttribute(provider.objectName, provider.attribute)
                log.trace "new value for {},{} was {}", sensor.name, provider.name, newValue
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
        entity.emit sensor, value
        value
    }
}

/**
 * Provides values to a sensor via JMX.
 */
public class JmxValueProvider<T> {
    public final String name
    public final String attribute
    public final ObjectName objectName
//    public final JmxSensorAdapter adapter
//    public final EntityLocal entity
    
    public Sensor<T> sensor

    public JmxValueProvider(String name, String attribute) { //, JmxSensorAdapter adapter, EntityLocal entity) {
        this.name = name
        this.attribute = attribute
        this.objectName = new ObjectName(name)
//        this.adapter = adapter
//        this.entity = entity
    }
    
    public connect(Sensor<T> sensor) {
        this.sensor = sensor
    }
    
    public T compute() {
//        def newValue = adapter.getAttribute(objectName, attribute)
//        entity.updateAttribute(sensor, newValue)
//        newValue
        null
    }
}