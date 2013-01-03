package brooklyn.event.adapter;

import javax.management.ObjectName


/** 
 * Provides convenient/fluent (and preferred) way to access a JMX object instance.
 * Allows one to access jmx-attributes, jmx-operations and jmx-notifications.
 * 
 * @see {@link JmxSensorAdapter} for recommended way of using this
 * 
 * @deprecated See brooklyn.event.feed.jmx.JmxFeed
 */
@Deprecated
public class JmxObjectNameAdapter {
    
	final JmxSensorAdapter adapter;
	final ObjectName objectName;
    
	public JmxObjectNameAdapter(JmxSensorAdapter adapter, ObjectName objectName) {
		this.adapter = adapter;
		this.objectName = objectName;
	}
    
    JmxReachableAdapter reachable(Map flags=[:]) {
        adapter.registry.register(new JmxReachableAdapter(flags, adapter, objectName));
    }

	JmxAttributeAdapter attribute(Map flags=[:], String attributeName) {
		adapter.registry.register(new JmxAttributeAdapter(flags, adapter, objectName, attributeName));
	}

    JmxOperationAdapter operation(String method, Object ...args) {
		adapter.registry.register(new JmxOperationAdapter(adapter, objectName, method, args));
	}
    
    /**
     * @param notificationType A regex for the notification type
     */
    JmxNotificationAdapter notification(String notificationType) {
        adapter.registry.register(new JmxNotificationAdapter(adapter, objectName, notificationType));
    }
}
