package brooklyn.event.adapter;

import groovy.time.TimeDuration
import groovy.transform.InheritConstructors

import javax.management.ObjectName

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.event.Sensor


/** 
 * Adapter that polls for a JMX attribute.
 * "Attribute" here refers to the JMX concept, rather than the brooklyn concept.
 * 
 * @see {@link JmxSensorAdapter} for recommended way of using this
 * 
 * @deprecated See brooklyn.event.feed.jmx.JmxFeed
 */
@Deprecated
@InheritConstructors
public class JmxAttributeAdapter extends AbstractSensorAdapter {
    public static final Logger log = LoggerFactory.getLogger(JmxAttributeAdapter.class);
    
	final JmxSensorAdapter adapter;
	final ObjectName objectName;
	final String attributeName;
	final AttributePollHelper poller;
	
	public JmxAttributeAdapter(Map flags=[:], JmxSensorAdapter adapter, ObjectName objectName, String attributeName) {
		super(flags);
		this.adapter = adapter;
// FIXME        adapter.addActivationLifecycleListeners({activateAdapter()},{deactivateAdapter()});
		poller = new AttributePollHelper(adapter, objectName, attributeName);
        poller.init();
		this.objectName = objectName;
		this.attributeName = attributeName;
	}
	
	static class AttributePollHelper extends AbstractPollHelper {
		JmxSensorAdapter adapter;
		ObjectName objectName;
		String attributeName;
		AttributePollHelper(JmxSensorAdapter adapter, ObjectName objectName, String attributeName) {
			super(adapter);
			this.adapter = adapter;
			this.objectName = objectName;
			this.attributeName = attributeName;
		}
		@Override
		protected AbstractSensorEvaluationContext executePollOnSuccess() {
			return new SingleValueResponseContext(value: adapter.helper.getAttribute(objectName, attributeName))
		}
	}
	/** optional postProcessing will take the result of the attribute invocation
	 * (its native type; casting to sensor's type is done on the return value of the closure) */
	public void poll(Sensor s, Closure postProcessing={it}) {
		poller.addSensor(s, postProcessing);
	}
	
	/** optional postProcessing will take the result of the attribute invocation
	 * (its native type; casting to sensor's type is done on the return value of the closure) */
	public void subscribe(Sensor s, Closure postProcessing={it}) {
		//TODO make a "notifications" stream and use it instead
		poll(s, postProcessing)
	}
    
    @Override
    protected void activateAdapter() {
        super.activateAdapter();
        try {
            if (adapter.checkObjectNameExistsNow(objectName)) {
                if (log.isDebugEnabled()) 
                    log.debug("For $entity ${adapter.helper.url}, MBean ${objectName} exists");
            } else {
                log.warn("For $entity ${adapter.helper.url}, MBean ${objectName} does not yet exist; continuing...");
            }
        } catch (Exception e) {
            log.warn("For $entity ${adapter.helper.url}, not reachable for MBean ${objectName}; continuing...", e);
        }
    }
}
