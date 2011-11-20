package brooklyn.event.adapter;

import java.io.IOException
import java.util.Map
import java.util.Set

import javax.management.JMX
import javax.management.MBeanServerConnection
import javax.management.NotificationFilter
import javax.management.NotificationListener
import javax.management.ObjectInstance
import javax.management.ObjectName
import javax.management.openmbean.CompositeData
import javax.management.openmbean.TabularData
import javax.management.remote.JMXConnector
import javax.management.remote.JMXConnectorFactory
import javax.management.remote.JMXServiceURL

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.basic.EntityLocal

public class JmxHelper {
	
	public static final Logger log = LoggerFactory.getLogger(JmxHelper.class);
	
	final JmxSensorAdapter adapter;
	final EntityLocal entity;
	JMXConnector jmxc
	MBeanServerConnection mbsc

	public static final Map<String,String> CLASSES = [
		"Integer" : Integer.TYPE.name,
		"Long" : Long.TYPE.name,
		"Boolean" : Boolean.TYPE.name,
		"Byte" : Byte.TYPE.name,
		"Character" : Character.TYPE.name,
		"Double" : Double.TYPE.name,
		"Float" : Float.TYPE.name,
		"GStringImpl" : String.class.getName(),
		"LinkedHashMap" : Map.class.getName(),
		"TreeMap" : Map.class.getName(),
		"HashMap" : Map.class.getName(),
		"ConcurrentHashMap" : Map.class.getName(),
		"TabularDataSupport" : TabularData.class.getName(),
		"CompositeDataSupport" : CompositeData.class.getName(),
	]
	
	public JmxHelper(JmxSensorAdapter adapter) {
		this.adapter = adapter;
		this.entity = adapter.entity;
	}
	
	public boolean isConnected() {
		return (jmxc && mbsc);
	}

	/** attempts to connect immediately */
	public synchronized void connect() throws IOException {
		if (jmxc) jmxc.close()
		JMXServiceURL url = new JMXServiceURL(adapter.url)
		Map env = [:]
		if (adapter.user && adapter.password) {
			String[] creds = [ adapter.user, adapter.password ]
			env.put(JMXConnector.CREDENTIALS, creds);
		}
		jmxc = JMXConnectorFactory.connect(url, env);
		mbsc = jmxc.getMBeanServerConnection();
	}
	
	/** continuously attempts to connect (blocking), for at least the indicated amount of time; or indefinitely if -1 */
	public boolean connect(long timeout) {
		log.debug "Connecting to JMX URL: {} ({})", adapter.url, ((timeout == -1) ? "indefinitely" : "${timeout}ms timeout")
		long start = System.currentTimeMillis()
		long end = start + timeout
		if (timeout == -1) end = Long.MAX_VALUE
		Throwable lastError;
		int attempt=0;
		while (start <= end) {
			start = System.currentTimeMillis()
			if (attempt==0) Thread.sleep(100);
			log.debug "trying connection to {}:{} at {}", adapter.host, adapter.rmiRegistryPort, start
			try {
				connect()
				return true
			} catch (IOException e) {
				log.debug "failed connection to {}:{} ({})", adapter.host, adapter.rmiRegistryPort, e.message
				lastError = e;
				//sleep 100 to prevent trashing and facilitate interruption
			}
			attempt++
		}
		log.warn("unable to connect to JMX url: ${adapter.url}", lastError);
		false
	}

	public synchronized void disconnect() {
		if (jmxc) {
			try {
				jmxc.close()
			} catch (Exception e) {
				log.warn("Caught exception disconnecting from JMX for at {}:{}, {}", entity, adapter.host, adapter.rmiRegistryPort, e.message)
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
			log.debug "entity {} got value {} for jmx attribute {}.{}", entity, result, objectName.canonicalName, attribute
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
			Class clazz = arg.getClass();
			signature[index] = (CLASSES.containsKey(clazz.simpleName) ? CLASSES.get(clazz.simpleName) : clazz.name);
		}
		def result = mbsc.invoke(objectName, method, arguments, signature)
		log.trace "got result {} for jmx operation {}.{}", result, objectName.canonicalName, method
		return result
	}

	public void addNotificationListener(String objectName, NotificationListener listener, NotificationFilter filter=null) {
		addNotificationListener(new ObjectName(objectName), listener, filter)
	}

	public void addNotificationListener(ObjectName objectName, NotificationListener listener, NotificationFilter filter=null) {
		ObjectInstance bean = findMBean objectName
		mbsc.addNotificationListener(objectName, listener, filter, null)
	}

    public void removeNotificationListener(String objectName, NotificationListener listener) {
        removeNotificationListener(new ObjectName(objectName), listener)
    }

    public void removeNotificationListener(ObjectName objectName, NotificationListener listener) {
        ObjectInstance bean = findMBean objectName
        mbsc.removeNotificationListener(objectName, listener, null, null)
    }

	public <M> M getProxyObject(String objectName, Class<M> mbeanInterface) {
		return getProxyObject(new ObjectName(objectName), mbeanInterface)
	}

	public <M> M getProxyObject(ObjectName objectName, Class<M> mbeanInterface) {
		return JMX.newMBeanProxy(mbsc, objectName, mbeanInterface, false)
	}
}