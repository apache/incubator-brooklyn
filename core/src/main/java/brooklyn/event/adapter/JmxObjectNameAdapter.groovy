package brooklyn.event.adapter;

import groovy.time.TimeDuration

import javax.management.ObjectName

import brooklyn.util.internal.LanguageUtils


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
    void checkExistsEventually(TimeDuration timeout) {
        boolean success = LanguageUtils.repeatUntilSuccess(timeout:timeout, "Wait for $objectName") {
            return adapter.helper.findMBean(objectName) != null
        }
        if (!success) {
            throw new IllegalStateException("MBean $objectName not found")
        }
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
