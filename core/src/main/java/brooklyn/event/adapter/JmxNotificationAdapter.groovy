package brooklyn.event.adapter

import groovy.lang.Closure

import java.util.Map

import javax.management.Notification
import javax.management.NotificationListener
import javax.management.ObjectName

import brooklyn.event.Sensor

/** 
 * Adapter that subscribes to a JMX notification.
 * 
 * @see {@link JmxSensorAdapter} for recommended way of using this
 */
class JmxNotificationAdapter extends AbstractSensorAdapter {
    final JmxSensorAdapter adapter
    final ObjectName objectName
    final String notificationName
    final NotificationPushHelper pusher
    
    public JmxNotificationAdapter(Map flags=[:], JmxSensorAdapter adapter, ObjectName objectName, String notificationName) {
        super(flags);
        this.adapter = adapter;
        pusher = new NotificationPushHelper(adapter, objectName, notificationName);
        this.objectName = objectName;
        this.notificationName = notificationName;
    }
    
    static class NotificationPushHelper extends AbstractPushHelper {
        final JmxSensorAdapter adapter;
        final ObjectName objectName
        final String notificationName
        final NotificationListener notificationListener
        
        NotificationPushHelper(JmxSensorAdapter adapter, ObjectName objectName, String notificationName) {
            super(adapter);
            this.adapter = adapter
            this.objectName = objectName
            this.notificationName = notificationName
            this.notificationListener = {Notification notif, Object callback ->
                    def wrappedVal = new SingleValueResponseContext(value:notif.getUserData())
                    onPush(wrappedVal) } as NotificationListener
        }
        @Override
        protected void activatePushing() {
            adapter.helper.addNotification(objectName, notificationListener)
        }
        @Override
        protected void deactivatePushing() {
            adapter.helper.removeNotificationListener(objectName, notificationListener)
        }
    }
    
    /** optional postProcessing will take the result of the attribute invocation
     * (its native type; casting to sensor's type is done on the return value of the closure) */
    public void subscribe(Sensor s, Closure postProcessing={it}) {
        pusher.addSensor(s, postProcessing);
    }
}
