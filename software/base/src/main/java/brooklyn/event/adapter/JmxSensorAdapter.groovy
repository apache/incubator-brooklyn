package brooklyn.event.adapter;

import groovy.time.TimeDuration

import java.util.concurrent.TimeUnit

import javax.management.ObjectInstance
import javax.management.ObjectName

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.basic.EntityLocal


/**
 * Entry point for wiring up brooklyn attributes to jmx; this doesn't evaluate any sensors directly,
 * but provides support for specific object-name/attribute combos etc.
 * <p>
 * Example usage:
 * <code>
 *   jmx.objectName('Brooklyn:type=MyExample,name=myName').with {
 *       attribute("myJmxAttribute").subscribe(MY_BROOKLYN_ATTRIBUTE)
 *       operation("myJmxOperation", "arg1").poll(MY_BROOKLYN_ATTRIBUTE_2)
 *       notification("myJmxNotification").subscribe(MY_BROOKLYN_ATTRIBUTE_3)
 *   }
 * </code>
 * 
 * @deprecated See brooklyn.event.feed.jmx.JmxFeed
 */
@Deprecated
public class JmxSensorAdapter extends AbstractSensorAdapter {

    public static final Logger log = LoggerFactory.getLogger(JmxSensorAdapter.class);
	public static final long JMX_CONNECTION_TIMEOUT_MS = 0; // i.e. by default don't block
	
	JmxHelper helper
	private volatile long jmxConnectionTimeout = JMX_CONNECTION_TIMEOUT_MS
 
	static {  // JMX ClientCommunicatorAdmin spits out scary warnings, but we just retry so don't worry
		// TODO better would be to capture and send to our logger as debug
		java.util.logging.Logger.getLogger("javax.management.remote.misc").setLevel(java.util.logging.Level.OFF); 
	}
	
	public JmxSensorAdapter(Map flags=[:], JmxHelper helper) {
		super(flags)
        this.helper = helper
	}

    public JmxSensorAdapter(Map flags=[:]) {
        super(flags)
    }

    public void setJmxConnectionTimeout(long val) {
        this.jmxConnectionTimeout = val
    }
    
    public String getConnectionUrl() {
        return helper?.getUrl()
    }
    
	void register(SensorRegistry registry) {
		super.register(registry)
 
		if (!helper) helper = new JmxHelper(entity)
		addActivationLifecycleListeners({ helper.connect(jmxConnectionTimeout) }, { helper.disconnect() })
	}

	public boolean isConnected() { super.isConnected() && helper.isConnected() }
			
	// might be nice:  syntax such as jmxAdapter.with { ...
	//			register(XXX, objectName("jboss.web:type=GlobalRequestProcessor,name=http-*").attribute("processingTime"), { it /* optional post-processing */ })
	//			onPostStart(JMX_URL, { getUrl() })
	//			onJmxError(ERROR_CHANNEL, { "JMX had error: "+errorCode } )
	
	public JmxObjectNameAdapter objectName(String val) { return objectName(new ObjectName(val)); }

    public JmxObjectNameAdapter objectName(ObjectName val) { return new JmxObjectNameAdapter(this, val); }
    
    public boolean checkObjectNameExistsNow(ObjectName objectName) {
        boolean connected = helper.connect(0);
        if (!connected) {
            log.warn("JMX management can't connect (using "+helper.url+"), to confirm existance of MBean "+objectName);
            return false;
        }
        
        Set<ObjectInstance> beans = helper.findMBeans(objectName);
        if (beans.isEmpty()) {
            log.warn("JMX management can't find MBean "+objectName+" (using "+helper.url+")");
        }
        return beans.size() > 0;
    }
    
    /** blocks (for default of 15s) until bean exists */
    public boolean checkObjectNameExists(ObjectName objectName, TimeDuration timeout=15*TimeUnit.SECONDS) {
        def beans = helper.doesMBeanExistsEventually(objectName, timeout);
        if (!beans) {
            log.warn("JMX management can't find MBean "+objectName+" (using "+helper.url+")");
        }
        return beans;
    }
}
