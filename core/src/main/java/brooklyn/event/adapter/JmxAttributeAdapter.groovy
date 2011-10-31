package brooklyn.event.adapter;

import groovy.transform.InheritConstructors

import javax.management.ObjectName

import brooklyn.event.Sensor


/** adapter which polls for a JMX attribute */
@InheritConstructors
public class JmxAttributeAdapter extends AbstractSensorAdapter {
	final JmxSensorAdapter adapter;
	final ObjectName objectName;
	final String attributeName;
	final AttributePollHelper poller;
	
	public JmxAttributeAdapter(Map flags=[:], JmxSensorAdapter adapter, ObjectName objectName, String attributeName) {
		super(flags);
		this.adapter = adapter;
		poller = new AttributePollHelper(adapter, objectName, attributeName);
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
}
