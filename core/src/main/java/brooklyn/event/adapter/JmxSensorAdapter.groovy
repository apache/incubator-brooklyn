package brooklyn.event.adapter

import javax.management.MBeanServerConnection
import javax.management.Notification
import javax.management.NotificationFilter
import javax.management.NotificationListener
import javax.management.ObjectInstance
import javax.management.ObjectName
import javax.management.openmbean.CompositeData
import javax.management.openmbean.TabularDataSupport
import javax.management.remote.JMXConnector
import javax.management.remote.JMXConnectorFactory
import javax.management.remote.JMXServiceURL

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.basic.Attributes
import brooklyn.entity.basic.EntityLocal
import brooklyn.event.basic.BasicNotificationSensor

import com.google.common.base.Preconditions

/**
 * This class adapts JMX {@link ObjectName} data to {@link brooklyn.event.Sensor} data
 * for a particular {@link brooklyn.entity.Entity}, updating the {@link Activity} as required.
 * 
 * The adapter normally polls the JMX server every second to update sensors, which could involve aggregation of data
 * or simply reading values and setting them in the attribute map of the activity model.
 */
public class JmxSensorAdapter {
    private static final Logger log = LoggerFactory.getLogger(JmxSensorAdapter.class);

    public static final String JMX_URL_FORMAT = "service:jmx:rmi:///jndi/rmi://%s:%d/%s"
    public static final String RMI_JMX_URL_FORMAT = "service:jmx:rmi://%s:%d/jndi/rmi://%s:%d/%s"

    private static final ENABLED = new NotificationFilter() {
        public boolean isNotificationEnabled(Notification notification) { true }
    }

    private static final Map<String,String> CLASSES = [
            "Integer" : Integer.TYPE.name,
            "Long" : Long.TYPE.name,
            "Boolean" : Boolean.TYPE.name,
            "Byte" : Byte.TYPE.name,
            "Character" : Character.TYPE.name,
            "Double" : Double.TYPE.name,
            "Float" : Float.TYPE.name,
            "LinkedHashMap" : Map.class.getName(),
            "TreeMap" : Map.class.getName(),
            "HashMap" : Map.class.getName(),
            "ConcurrentHashMap" : Map.class.getName(),
        ]
 
    final EntityLocal entity
    final String host
    final Integer rmiRegistryPort
    final Integer rmiServerPort
    final String context
    final String url
    
    JMXConnector jmxc
    MBeanServerConnection mbsc
 
    public JmxSensorAdapter(EntityLocal entity, long timeout = -1) {
        this.entity = entity
 
        host = entity.getAttribute(Attributes.HOSTNAME);
        rmiRegistryPort = entity.getAttribute(Attributes.JMX_PORT);
        rmiServerPort = entity.getAttribute(Attributes.RMI_PORT);
        context = entity.getAttribute(Attributes.JMX_CONTEXT);
 
        if (rmiServerPort) {
	        url = String.format(RMI_JMX_URL_FORMAT, host, rmiServerPort, host, rmiRegistryPort, context)
        } else {
	        url = String.format(JMX_URL_FORMAT, host, rmiRegistryPort, context)
        }

        if (!connect(timeout)) throw new IllegalStateException("Could not connect to JMX service on ${host}:${rmiRegistryPort}")
    }

    public <T> ValueProvider<T> newAttributeProvider(String objectName, String attribute) {
        return new JmxAttributeProvider(this, new ObjectName(objectName), attribute)
    }

    public <T> ValueProvider<T> newOperationProvider(String objectName, String method, Object...arguments) {
        return new JmxOperationProvider(this, new ObjectName(objectName), method, arguments)
    }

    public ValueProvider<HashMap> newTabularDataProvider(String objectName, String attribute) {
        return new JmxTabularDataProvider(this, new ObjectName(objectName), attribute)
    }

    public JmxAttributeNotifier newAttributeNotifier(String objectName, String attribute, EntityLocal entity, BasicNotificationSensor sensor) {
        return new JmxAttributeNotifier(this, new ObjectName(objectName), attribute, entity, sensor)
    }
    
    public boolean isConnected() {
        return (jmxc && mbsc);
    }
 
    /** attempts to connect immediately */
    public void connect() throws IOException {
        if (jmxc) jmxc.close()
        JMXServiceURL url = new JMXServiceURL(url)
        Hashtable env = new Hashtable();
        String user = entity.getAttribute(Attributes.JMX_USER);
        String password = entity.getAttribute(Attributes.JMX_PASSWORD);
        if (user && password) {
			String[] creds = [ user, password ]
			env.put(JMXConnector.CREDENTIALS, creds);
        }
        jmxc = JMXConnectorFactory.connect(url, env);
        mbsc = jmxc.getMBeanServerConnection();
    }
 
    /** continuously attempts to connect (blocking), for at least the indicated amount of time; or indefinitely if -1 */
    public boolean connect(long timeout) {
        log.info "Connecting to JMX URL: {} ({})", url, ((timeout == -1) ? "indefinitely" : "${timeout}ms timeout")
        long start = System.currentTimeMillis()
        long end = start + timeout
        if (timeout == -1) end = Long.MAX_VALUE
        Throwable lastError;
        while (start <= end) {
            start = System.currentTimeMillis()
            log.debug "trying connection to {}:{} at {}", host, rmiRegistryPort, start
            try {
                connect()
                return true
            } catch (IOException e) {
                log.debug "failed connection to {}:{} ({})", host, rmiRegistryPort, e.message
                lastError = e;
            }
        }
        log.warn("unable to connect to JMX url: ${url}", lastError);
        false
    }

    public void disconnect() {
        if (jmxc) {
            try {
	            jmxc.close()
            } catch (Exception e) {
                log.warn("Caught exception disconnecting from JMX at {}:{}, {}", host, rmiRegistryPort, e.message)
            } finally {
	            jmxc = null
	            mbsc = null
            }
        }
    }

    public void checkConnected() {
        if (!isConnected()) throw new IllegalStateException("Not connected to JMX for entity $entity")
    }

    public ObjectInstance findMBean(ObjectName objectName) {
        Set<ObjectInstance> beans = mbsc.queryMBeans(objectName, null)
        if (beans.isEmpty() || beans.size() > 1) {
            log.warn "JMX object name query returned {} values for {}", beans.size(), objectName.canonicalName
            return null
        }
        ObjectInstance bean = beans.find { true }
        return bean
    }

    /**
     * Returns a specific attribute for a JMX {@link ObjectName}.
     */
    public Object getAttribute(ObjectName objectName, String attribute) {
        checkConnected()
        
        ObjectInstance bean = findMBean objectName
        if (bean != null) {
            def result = mbsc.getAttribute(bean.objectName, attribute)
            log.trace "got value {} for jmx attribute {}.{}", result, objectName.canonicalName, attribute
            return result
        } else {
            return null
        }
    }

    /** @see #operation(ObjectName, String, Object...) */
    public Object operation(String objectName, String method, Object...arguments) {
        return operation(new ObjectName(objectName), method, arguments)
    }

    /**
     * Executes an operation on a JMX {@link ObjectName}.
     */
    public Object operation(ObjectName objectName, String method, Object...arguments) {
        checkConnected()
        
        ObjectInstance bean = findMBean objectName
        String[] signature = new String[arguments.length]
        arguments.eachWithIndex { arg, int index ->
            Class clazz = arg.getClass()
            signature[index] = (CLASSES.containsKey(clazz.simpleName) ? CLASSES.get(clazz.simpleName) : clazz.name)
        }
        def result = mbsc.invoke(objectName, method, arguments, signature)
        log.trace "got result {} for jmx operation {}.{}", result, objectName.canonicalName, method
        return result
    }

    public Object operation(ObjectName objectName, String method) {
        return operation(objectName, method, [] as Object[], [] as String[])
    }
    
    public Object invokeOperation(String objectName, String method, Object[] arguments, Object[] signature) {
        return invokeOperation(new ObjectName(objectName), method, arguments, signature)
    }
    
    /**
     * Executes an operation on a JMX {@link ObjectName}.
     */
    public Object invokeOperation(ObjectName objectName, String method, Object[] arguments, Object[] signature) {
        checkConnected()
        
        ObjectInstance bean = findMBean objectName
        def result = mbsc.invoke(objectName, method, arguments, signature)
        log.trace "got result {} for jmx operation {}.{}", result, objectName.canonicalName, method
        return result
    }
    
    private void addNotification(ObjectName objectName, String attribute, NotificationListener listener) {
        ObjectInstance bean = findMBean objectName
        mbsc.addNotificationListener(objectName, listener, ENABLED, null)
    }
}

/**
 * Provides JMX attribute values to a sensor.
 */
public class JmxAttributeProvider<T> implements ValueProvider<T> {
    private final JmxSensorAdapter adapter
    private final ObjectName objectName
    private final String attribute
    
    public JmxAttributeProvider(JmxSensorAdapter adapter, ObjectName objectName, String attribute) {
        this.adapter = Preconditions.checkNotNull(adapter, "adapter")
        this.objectName = Preconditions.checkNotNull(objectName, "object name")
        this.attribute = Preconditions.checkNotNull(attribute, "attribute")
    }
    
    public T compute() {
        return adapter.getAttribute(objectName, attribute)
    }
}

/**
 * Provides JMX operation results to a sensor.
 */
public class JmxOperationProvider<T> implements ValueProvider<T> {
    private final JmxSensorAdapter adapter
    private final ObjectName objectName
    private final String method
    private final Object[] arguments
    
    public JmxOperationProvider(JmxSensorAdapter adapter, ObjectName objectName, String method, Object...arguments) {
        this.adapter = Preconditions.checkNotNull(adapter, "adapter")
        this.objectName = Preconditions.checkNotNull(objectName, "object name")
        this.method = Preconditions.checkNotNull(method, "method")
        this.arguments = arguments
    }
    
    public T compute() {
        return adapter.operation(objectName, method, arguments)
    }
}

public class JmxTabularDataProvider implements ValueProvider<Map<String, Object>> {

    private static final Logger log = LoggerFactory.getLogger(JmxTabularDataProvider.class);

    private final JmxSensorAdapter adapter
    private final ObjectName objectName
    private final String attribute

    public JmxTabularDataProvider(JmxSensorAdapter adapter, ObjectName objectName, String attribute) {
        this.adapter = Preconditions.checkNotNull(adapter, "adapter")
        this.objectName = Preconditions.checkNotNull(objectName, "object name")
        this.attribute = Preconditions.checkNotNull(attribute, "attribute")
    }

    public Map<String, Object> compute() {
        HashMap<String, Object> out = []
        Object attr = adapter.getAttribute(objectName, attribute)
        TabularDataSupport table
        try {
            table = (TabularDataSupport) attr;
        } catch (ClassCastException e) {
            log.error "($objectName, '$attribute') gave instance of ${attr.getClass()}, expected ${TabularDataSupport.class}"
            throw e
        }
        for (Object entry : table.values()) {
            CompositeData data = (CompositeData) entry //.getValue()
            data.getCompositeType().keySet().each { String key ->
                def old = out.put(key, data.get(key))
                if (old) {
                    log.warn "JmxTabularDataProvider has overwritten key {}", key
                }
            }
        }
        return out
    }
}

/**
 * Provides JMX attribute values to a sensor.
 */
public class JmxAttributeNotifier implements NotificationListener {
    private final JmxSensorAdapter adapter
    private final ObjectName objectName
    private final String attribute
    private final EntityLocal entity
    private final BasicNotificationSensor sensor
    
    public JmxAttributeNotifier(JmxSensorAdapter adapter, ObjectName objectName, String attribute, EntityLocal entity, BasicNotificationSensor sensor) {
        this.adapter = Preconditions.checkNotNull(adapter, "adapter")
        this.objectName = Preconditions.checkNotNull(objectName, "object name")
        this.attribute = Preconditions.checkNotNull(attribute, "attribute")
        this.entity = Preconditions.checkNotNull(entity, "entity")
        this.sensor = Preconditions.checkNotNull(sensor, "sensor")
        
        adapter.addNotification(objectName, attribute, this)
    }
    
    public void handleNotification(Notification notification, Object handback) {
        if (notification.type.equals(sensor.name)) {
            entity.setAttribute(sensor, notification.userData)
        }
    }
}
