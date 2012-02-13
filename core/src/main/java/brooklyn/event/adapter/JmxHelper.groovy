package brooklyn.event.adapter;

import static com.google.common.base.Preconditions.checkNotNull
import groovy.time.TimeDuration

import java.io.IOException
import java.util.Map
import java.util.Set
import java.util.concurrent.TimeUnit

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

import brooklyn.entity.basic.Attributes
import brooklyn.entity.basic.EntityLocal
import brooklyn.util.internal.LanguageUtils
import brooklyn.util.internal.TimeExtras

public class JmxHelper {
	
	protected static final Logger LOG = LoggerFactory.getLogger(JmxHelper.class);

    static { TimeExtras.init() }
    
    public static final String JMX_URL_FORMAT = "service:jmx:rmi:///jndi/rmi://%s:%d/%s"
    public static final String RMI_JMX_URL_FORMAT = "service:jmx:rmi://%s:%d/jndi/rmi://%s:%d/%s"

    // Tracks the MBeans we have failed to find, with a set keyed off the url
    private static final Map<String,ObjectName> notFoundMBeansByUrl = Collections.synchronizedMap(new WeakHashMap<ObjectName,Set<ObjectName>>())

    final String url
    final String user
    final String password

	JMXConnector jmxc
	MBeanServerConnection mbsc
    boolean triedConnecting
    
    // Tracks the MBeans we have failed to find for this JmsHelper's connection URL (so can log just once for each)
    private final Set<ObjectName> notFoundMBeans
    
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

    public static String toConnectorUrl(String host, Integer rmiRegistryPort, Integer rmiServerPort, String context) {
        if (rmiServerPort) {
            return String.format(RMI_JMX_URL_FORMAT, host, rmiServerPort, host, rmiRegistryPort, context)
        } else {
            return String.format(JMX_URL_FORMAT, host, rmiRegistryPort, context)
        }
    }
    
    public static String toConnectorUrl(EntityLocal entity) {
        String url = entity.getAttribute(Attributes.JMX_SERVICE_URL)
        if (url != null) {
            return url
        } else {
            String host = checkNotNull(entity.getAttribute(Attributes.HOSTNAME))
            Integer rmiRegistryPort = entity.getAttribute(Attributes.JMX_PORT)
            Integer rmiServerPort = entity.getAttribute(Attributes.RMI_PORT)
            String context = entity.getAttribute(Attributes.JMX_CONTEXT)
            
            return toConnectorUrl(host, rmiRegistryPort, rmiServerPort, context)
        }
    }
    
    public JmxHelper(String url) {
        this(url, null, null)
    }
    
    public JmxHelper(EntityLocal entity) {
        this(toConnectorUrl(entity), entity.getAttribute(Attributes.JMX_USER), entity.getAttribute(Attributes.JMX_PASSWORD))
    }
    
    public JmxHelper(String url, String user, String password) {
        this.url = url
        this.user = user
        this.password = password
        
        synchronized (notFoundMBeansByUrl) {
            Set<ObjectName> set = notFoundMBeansByUrl.get(url)
            if (set == null) {
                set = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<ObjectName,Boolean>()))
                notFoundMBeansByUrl.put(url, set)
            }
            notFoundMBeans = set;
        }
    }
    
	public boolean isConnected() {
		return (jmxc && mbsc);
	}

	/** attempts to connect immediately */
	public synchronized void connect() throws IOException {
        triedConnecting = true
		if (jmxc) jmxc.close()
		JMXServiceURL url = new JMXServiceURL(url)
		Map env = [:]
		if (user && password) {
			String[] creds = [ user, password ]
			env.put(JMXConnector.CREDENTIALS, creds);
		}
		jmxc = JMXConnectorFactory.connect(url, env);
		mbsc = jmxc.getMBeanServerConnection();
	}
	
	/** continuously attempts to connect (blocking), for at least the indicated amount of time; or indefinitely if -1 */
	public boolean connect(long timeout) {
		LOG.debug "Connecting to JMX URL: {} ({})", url, ((timeout == -1) ? "indefinitely" : "${timeout}ms timeout")
		long start = System.currentTimeMillis()
		long end = start + timeout
		if (timeout == -1) end = Long.MAX_VALUE
		Throwable lastError;
		int attempt=0;
		while (start <= end) {
			start = System.currentTimeMillis()
			if (attempt!=0) Thread.sleep(100); //sleep 100 to prevent trashing and facilitate interruption
			LOG.trace "trying connection to {} at time {}", url, start
			try {
				connect()
				return true
            } catch (SecurityException e) {
                LOG.debug "Attempt {} failed connecting to {} ({})", attempt+1, url, e.message
                lastError = e;
			} catch (IOException e) {
				LOG.debug "Attempt {} failed connecting to {} ({})", attempt+1, url, e.message
				lastError = e;
            } catch (NumberFormatException e) {
                LOG.warn "Failed connection to {} ({}); rethrowing...", url, e.message
                throw e
			}
			attempt++
		}
		LOG.warn("unable to connect to JMX url: ${url}", lastError);
		false
	}

	public synchronized void disconnect() {
        triedConnecting = false
		if (jmxc) {
            LOG.debug "Disconnecting from JMX URL {}", url
			try {
				jmxc.close()
			} catch (Exception e) {
				LOG.warn("Caught exception disconnecting from JMX at {} ({})", url, e.message)
			} finally {
				jmxc = null
				mbsc = null
			}
		}
	}

	public void checkConnected() {
		if (!isConnected()) {
            if (triedConnecting) {
                throw new IllegalStateException("Failed to connect to JMX at $url")
            } else {
                throw new IllegalStateException("Not connected (and not attempted to connect) to JMX at $url")
            }
		} 
	}

    public Set<ObjectInstance> findMBeans(ObjectName objectName) {
        return mbsc.queryMBeans(objectName, null)
    }
    
    public ObjectInstance findMBean(ObjectName objectName) {
	Set<ObjectInstance> beans = findMBeans(objectName)
        if (beans.size() > 1) {
            LOG.warn "JMX object name query returned {} values for {}; ignoring all", beans.size(), objectName.canonicalName
            return null
        } else if (beans.isEmpty()) {
            boolean changed = notFoundMBeans.add(objectName)
            if (changed) {
                LOG.warn "JMX object {} not found at {}", objectName.canonicalName, url
            } else {
                LOG.debug "JMX object {} not found at {} (repeating)", objectName.canonicalName, url
            }
            return null
        } else {
            notFoundMBeans.remove(objectName)
            ObjectInstance bean = beans.find { true }
            return bean
        }
    }

    public void checkMBeanExistsEventually(ObjectName objectName, long timeoutMillis) {
        checkMBeanExistsEventually(objectName, timeoutMillis*TimeUnit.MILLISECONDS)
    }
    
    public void checkMBeanExistsEventually(ObjectName objectName, TimeDuration timeout) {
        Set<ObjectInstance> beans = [] as Set
        boolean success = LanguageUtils.repeatUntilSuccess(timeout:timeout, "Wait for $objectName") {
            beans = findMBeans(objectName)
            return beans.size() == 1
        }
        if (!success) {
            throw new IllegalStateException("MBean $objectName not found within $timeout" +
                    (beans.size() > 1 ? "; found multiple matches: $beans" : ""))
        }
    }
    
	/**
	 * Returns a specific attribute for a JMX {@link ObjectName}.
	 */
	public Object getAttribute(ObjectName objectName, String attribute) {
		checkConnected()

		ObjectInstance bean = findMBean objectName
		if (bean != null) {
			def result = mbsc.getAttribute(bean.objectName, attribute)
			LOG.trace "From {}, for jmx attribute {}.{}, got value {}", url, objectName.canonicalName, attribute, result
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
		LOG.trace "From {}, for jmx operation {}.{}, got value {}", url, objectName.canonicalName, method, result
		return result
	}

	public void addNotificationListener(String objectName, NotificationListener listener, NotificationFilter filter=null) {
		addNotificationListener(new ObjectName(objectName), listener, filter)
	}

	public void addNotificationListener(ObjectName objectName, NotificationListener listener, NotificationFilter filter=null) {
		mbsc.addNotificationListener(objectName, listener, filter, null)
	}

    public void removeNotificationListener(String objectName, NotificationListener listener) {
        removeNotificationListener(new ObjectName(objectName), listener)
    }

    public void removeNotificationListener(ObjectName objectName, NotificationListener listener) {
        if (mbsc) mbsc.removeNotificationListener(objectName, listener, null, null)
    }

	public <M> M getProxyObject(String objectName, Class<M> mbeanInterface) {
		return getProxyObject(new ObjectName(objectName), mbeanInterface)
	}

	public <M> M getProxyObject(ObjectName objectName, Class<M> mbeanInterface) {
		return JMX.newMBeanProxy(mbsc, objectName, mbeanInterface, false)
	}
}
