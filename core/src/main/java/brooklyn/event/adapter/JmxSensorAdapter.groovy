package brooklyn.event.adapter;

import groovy.lang.Closure
import groovy.transform.InheritConstructors

import java.io.IOException
import java.util.Map

import javax.management.JMX
import javax.management.MBeanServerConnection
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
import brooklyn.event.Sensor


/** entry point for jmx sensor adapters; this doesn't evaluate any sensors directly,
 * but provides support for specific object-name/attribute combos etc
 */
public class JmxSensorAdapter extends AbstractSensorAdapter {

	public static final String JMX_URL_FORMAT = "service:jmx:rmi:///jndi/rmi://%s:%d/%s"
	public static final String RMI_JMX_URL_FORMAT = "service:jmx:rmi://%s:%d/jndi/rmi://%s:%d/%s"
 
	public static final long JMX_CONNECTION_TIMEOUT_MS = 120000;
	
	String host
	Integer rmiRegistryPort
	Integer rmiServerPort
	String context
	String url
	
	JmxHelper helper
 
	static {  // JMX ClientCommunicatorAdmin spits out scary warnings, but we just retry so don't worry
		// TODO better would be to capture and send to our logger as debug
		java.util.logging.Logger.getLogger("javax.management.remote.misc").setLevel(java.util.logging.Level.OFF); 
	}
	
	public JmxSensorAdapter(Map flags=[:]) {
		super(flags)
	}

	void register(SensorRegistry registry) {
		super.register(registry)
 
		host = entity.getAttribute(Attributes.HOSTNAME);
		rmiRegistryPort = entity.getAttribute(Attributes.JMX_PORT);
		rmiServerPort = entity.getAttribute(Attributes.RMI_PORT);
		context = entity.getAttribute(Attributes.JMX_CONTEXT);
 
		if (rmiServerPort) {
			url = String.format(RMI_JMX_URL_FORMAT, host, rmiServerPort, host, rmiRegistryPort, context)
		} else {
			url = String.format(JMX_URL_FORMAT, host, rmiRegistryPort, context)
		}
		helper = new JmxHelper(this)
		addActivationLifecycleListeners({ helper.connect(JMX_CONNECTION_TIMEOUT_MS) }, { helper.disconnect() })
	}

	public boolean isConnected() { super.isConnected() && helper.isConnected() }
			
	//TODO notifications
	//TODO tabular

	// might be nice:  syntax such as jmxAdapter.with { ...
	//			register(XXX, objectName("jboss.web:type=GlobalRequestProcessor,name=http-*").attribute("processingTime"), { it /* optional post-processing */ })
	//			onPostStart(JMX_URL, { getUrl() })
	//			onJmxError(ERROR_CHANNEL, { "JMX had error: "+errorCode } )
	
	public JmxObjectNameAdapter objectName(String objectName) { return new JmxObjectNameAdapter(this, new ObjectName(objectName)); }
	
}
