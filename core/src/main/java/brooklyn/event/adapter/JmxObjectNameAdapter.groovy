package brooklyn.event.adapter;

import javax.management.ObjectName


/** 
 * Provides convenient/fluent (and preferred) way to access a JMX object instance.
 * Allows one to access jmx-attributes, jmx-operations and jmx-notifications.
 * 
 * @see {@link JmxSensorAdapter} for recommended way of using this
 */
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
    JmxNotificationAdapter notification(String notificationName) {
        adapter.registry.register(new JmxNotificationAdapter(adapter, objectName, notificationName));
    }
}
