package brooklyn.event.adapter;

import javax.management.ObjectName


/** provides convenient/fluent (and preferred) way to access JMX attributes and operations */
public class JmxObjectNameAdapter {
	final JmxSensorAdapter adapter;
	final ObjectName objectName;
	public JmxObjectNameAdapter(JmxSensorAdapter adapter, ObjectName objectName) {
		this.adapter = adapter;
		this.objectName = objectName;
	}
	JmxAttributeAdapter attribute(String attributeName) {
		adapter.registry.register(new JmxAttributeAdapter(adapter, objectName, attributeName));
	}
	JmxOperationAdapter operation(String method, Object ...args) {
		adapter.registry.register(new JmxOperationAdapter(adapter, objectName, method, args));
	}
	//TODO notifications
}
