package brooklyn.event.adapter

import javax.management.MBeanServerConnection
import javax.management.ObjectInstance
import javax.management.ObjectName
import javax.management.remote.JMXConnector
import javax.management.remote.JMXConnectorFactory
import javax.management.remote.JMXServiceURL

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.basic.Attributes
import brooklyn.entity.basic.EntityLocal

import com.google.common.base.Preconditions

/**
 * This class adapts JMX {@link ObjectName} dfata to {@link Sensor} data for a particular {@link Entity}, updating the
 * {@link Activity} as required.
 * 
 *  The adapter normally polls the JMX server every second to update sensors, which could involve aggregation of data
 *  or simply reading values and setting them in the attribute map of the activity model.
 */
public class JmxSensorAdapter {
    static final Logger log = LoggerFactory.getLogger(JmxSensorAdapter.class);
 
    final EntityLocal entity
    final String jmxUrl
    
    JMXConnector jmxc
    MBeanServerConnection mbsc
 
    public JmxSensorAdapter(EntityLocal entity, long timeout = -1) {
        this.entity = entity
 
        String host = entity.getAttribute(Attributes.JMX_HOST);
        int port = entity.getAttribute(Attributes.JMX_PORT);
 
        this.jmxUrl = "service:jmx:rmi:///jndi/rmi://${host}:${port}/jmxrmi";
        
        if (!connect(timeout)) throw new IllegalStateException("Could not connect to JMX service")
    }

    public <T> ValueProvider<T> newValueProvider(String objectName, String attribute) {
        return new JmxValueProvider(new ObjectName(objectName), attribute, this)
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
        while (thisStartTime <= endTime) {
            thisStartTime = System.currentTimeMillis()
            log.debug "trying connection to {} (at {})", jmxUrl, thisStartTime
            try {
                connect()
                return true
            } catch (IOException e) {
                log.debug "failed connection to {} ({} at {})", jmxUrl, e.message, System.currentTimeMillis()
            }
        }
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

    /**
     * Returns a specific attribute for a JMX {@link ObjectName}.
     */
    private Object getAttribute(ObjectName objectName, String attribute) {
        checkConnected()
        
        Set<ObjectInstance> beans = mbsc.queryMBeans(objectName, null)
        if (beans.isEmpty() || beans.size() > 1) {
            log.warn "JMX object name query returned {} values for {}", beans.size(), objectName.canonicalName
            return null
        }
        ObjectInstance bean = beans.find { true }
        def result = mbsc.getAttribute(bean.objectName, attribute)
        log.trace "got value {} for jmx attribute {}.{}", result, objectName.canonicalName, attribute
        return result
    }
}

/**
 * Provides values to a sensor via JMX.
 */
public class JmxValueProvider<T> implements ValueProvider<T> {
    private final ObjectName objectName
    private final String attribute
    private final JmxSensorAdapter adapter
    
    public JmxValueProvider(ObjectName objectName, String attribute, JmxSensorAdapter adapter) {
        this.objectName = Preconditions.checkNotNull(objectName, "object name")
        this.attribute = Preconditions.checkNotNull(attribute, "attribute")
        this.adapter = Preconditions.checkNotNull(adapter, "adapter")
    }
    
    public T compute() {
        return adapter.getAttribute(objectName, attribute)
    }
}
