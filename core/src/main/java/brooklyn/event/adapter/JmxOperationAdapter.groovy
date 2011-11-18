package brooklyn.event.adapter;

import groovy.transform.InheritConstructors

import javax.management.ObjectName

import brooklyn.event.Sensor


/** 
 * Adapter that periodically calls a JMX operation.
 * 
 * @see {@link JmxSensorAdapter} for recommended way of using this
 */
@InheritConstructors
public class JmxOperationAdapter extends AbstractSensorAdapter {
	final JmxSensorAdapter adapter;
	final ObjectName objectName;
	final String methodName;
	Object[] args = [];
	
	public JmxOperationAdapter(Map flags=[:], JmxSensorAdapter adapter, ObjectName objectName, String methodName, Object ...args) {
		super(flags);
		this.adapter = adapter;
		this.objectName = objectName;
		this.methodName = methodName;
		this.args = args;
		poller = new OperationPollHelper(adapter, objectName, methodName, args);
	}
			
	protected final OperationPollHelper poller;
	
	static class OperationPollHelper extends AbstractPollHelper {
		final JmxSensorAdapter adapter;
		final ObjectName objectName;
		final String methodName;
		Object[] args = [];
		OperationPollHelper(JmxSensorAdapter adapter, ObjectName objectName, String methodName, Object[] args) {
			super(adapter)
			this.adapter = adapter;
			this.objectName = objectName;
			this.methodName = methodName;
			this.args = args;
		}
		@Override
		protected AbstractSensorEvaluationContext executePollOnSuccess() {
			return new SingleValueResponseContext(value: adapter.helper.operation(objectName, methodName, args))
		}
	}
	/** optional postProcessing will take the result of the operation invocation
	 * (its native type; casting to sensor's type is done on the return value of the closure) */
	public void poll(Sensor s, Closure postProcessing={it}) {
		poller.addSensor(s, postProcessing);
	}
}