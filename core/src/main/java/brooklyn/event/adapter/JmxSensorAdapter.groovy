package brooklyn.event.adapter

import javax.management.MBeanServerConnection
import javax.management.Notification;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectInstance
import javax.management.ObjectName
import javax.management.remote.JMXConnector
import javax.management.remote.JMXConnectorFactory
import javax.management.remote.JMXServiceURL

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.basic.Attributes
import brooklyn.entity.basic.EntityLocal
import brooklyn.event.AttributeSensor

import com.google.common.base.Preconditions
import javax.management.openmbean.TabularDataSupport
import javax.management.openmbean.CompositeData

/**
 * This class adapts JMX {@link ObjectName} data to {@link brooklyn.event.Sensor} data
 * for a particular {@link brooklyn.entity.Entity}, updating the {@link Activity} as required.
 * 
 * The adapter normally polls the JMX server every second to update sensors, which could involve aggregation of data
 * or simply reading values and setting them in the attribute map of the activity model.
 */
public class JmxSensorAdapter {
    private static final Logger log = LoggerFactory.getLogger(JmxSensorAdapter.class);
    
    private static final ENABLED = new NotificationFilter() {
        public boolean isNotificationEnabled(Notification notification) { true }
    }
    
    private static final Map<String,String> PRIMITIVES = [
            "Integer" : Integer.TYPE.name,
            "Long" : Long.TYPE.name,
            "Boolean" : Boolean.TYPE.name,
            "Byte" : Byte.TYPE.name,
            "Character" : Character.TYPE.name,
            "Double" : Double.TYPE.name,
            "Float" : Float.TYPE.name,
        ]
 
    final EntityLocal entity
    final String jmxUrl
    
    JMXConnector jmxc
    MBeanServerConnection mbsc
 
    public JmxSensorAdapter(EntityLocal entity, long timeout = -1) {
        this.entity = entity
 
        String host = entity.getAttribute(Attributes.HOSTNAME);
        int port = entity.getAttribute(Attributes.JMX_PORT);
 
        this.jmxUrl = "service:jmx:rmi:///jndi/rmi://${host}:${port}/jmxrmi";
        
        if (!connect(timeout)) throw new IllegalStateException("Could not connect to JMX service")
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
    
    public boolean isConnected() {
        return (jmxc && mbsc);
    }
 
    /** attempts to connect immediately */
    public void connect() throws IOException {
        if (jmxc) jmxc.close()
        JMXServiceURL url = new JMXServiceURL(jmxUrl)
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
    public boolean connect(long timeoutMillis) {
        log.debug "invoking connect to {}", jmxUrl
        long thisStartTime = System.currentTimeMillis()
        long endTime = thisStartTime + timeoutMillis
        if (timeoutMillis==-1) endTime = Long.MAX_VALUE
        Throwable lastError;
        while (thisStartTime <= endTime) {
            thisStartTime = System.currentTimeMillis()
            log.debug "trying connection to {} (at {})", jmxUrl, thisStartTime
            try {
                connect()
                return true
            } catch (IOException e) {
                log.debug "failed connection to {} ({} at {})", jmxUrl, e.message, System.currentTimeMillis()
                lastError = e;
            }
        }
        log.warn("unable to connect to JMX jmxUrl", lastError);
        false
    }

    public void disconnect() {
        if (jmxc) {
            jmxc.close()
            jmxc = null
            mbsc = null
        }
    }

    public void checkConnected() {
        if (!isConnected()) throw new IllegalStateException("Not connected to JMX for entity $entity")
    }

    private ObjectInstance findMBean(ObjectName objectName) {
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
    private Object getAttribute(ObjectName objectName, String attribute) {
        checkConnected()
        
        ObjectInstance bean = findMBean objectName
        def result = mbsc.getAttribute(bean.objectName, attribute)
        log.trace "got value {} for jmx attribute {}.{}", result, objectName.canonicalName, attribute
        return result
    }

    /**
     * Executes an operation on a JMX {@link ObjectName}.
     */
    private Object operation(ObjectName objectName, String method, Object...arguments) {
        checkConnected()
        
        ObjectInstance bean = findMBean objectName
        String[] signature = new String[arguments.size()]
        arguments.eachWithIndex { it, int index ->
            signature[index] =
                (PRIMITIVES.keySet().contains(it.class.simpleName) ? PRIMITIVES.get(it.class.simpleName) : it.class.name)
        }
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
        // Entry set is really Map.Entry<List<?>,CompositeData>
        for(Map.Entry<Object, Object> entry : table.entrySet()) {
            CompositeData data = (CompositeData) entry.getValue()
            data.getCompositeType().keySet().each {
                def previous = out.put(it, data.get(it))
                if (previous != null) {
                    log.warn "JmxTabularDataProvider has overwritten key $it"
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
    private final AttributeSensor sensor
    
    public JmxAttributeNotifier(JmxSensorAdapter adapter, ObjectName objectName, String attribute, EntityLocal entity, AttributeSensor sensor) {
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
