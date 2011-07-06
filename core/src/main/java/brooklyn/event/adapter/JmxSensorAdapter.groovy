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

import com.google.common.base.Preconditions;

import brooklyn.entity.basic.Attributes
import brooklyn.entity.basic.EntityLocal
import brooklyn.event.AttributeSensor
import brooklyn.event.Sensor

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
 
        this.jmxUrl = "service:jmx:rmi:///jndi/rmi://"+host+":"+port+"/jmxrmi";
        
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
        jmxc = JMXConnectorFactory.connect(url, null);
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
            log.warn("JMX object name query returned ${beans.size()} values. Object name was: ${objectName.getCanonicalName()}")
            return null
        }
        ObjectInstance bean = beans.find { true }
        def result = mbsc.getAttribute(bean.getObjectName(), attribute)
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
