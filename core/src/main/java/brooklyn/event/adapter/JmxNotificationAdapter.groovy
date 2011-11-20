package brooklyn.event.adapter

import groovy.lang.Closure

import java.util.Map
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList;

import javax.management.Notification
import javax.management.NotificationFilter
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
    final String notificationType
    final NotificationPushHelper pusher
    
    public JmxNotificationAdapter(Map flags=[:], JmxSensorAdapter adapter, ObjectName objectName, String notificationType) {
        super(flags);
        this.adapter = adapter;
        pusher = new NotificationPushHelper(adapter, objectName, notificationType);
        this.objectName = objectName;
        this.notificationType = notificationType;
    }
    
    static class NotificationPushHelper extends AbstractPushHelper {
        final JmxSensorAdapter adapter;
        final ObjectName objectName
        final String notificationType
        final NotificationListener notificationListener
        final NotificationFilter notificationFilter
        final List<NotificationListener> pushedListeners = [] as CopyOnWriteArrayList
        
        NotificationPushHelper(JmxSensorAdapter adapter, ObjectName objectName, String notificationType) {
            super(adapter);
            this.adapter = adapter
            this.objectName = objectName
            this.notificationType = notificationType
            this.notificationListener = {Notification notif, Object callback ->
                    def wrappedVal = new SingleValueResponseContext(value:notif.getUserData())
                    onPush(wrappedVal)
                    notifyListeners(notif) } as NotificationListener
            this.notificationFilter = JmxNotificationFilters.matchesTypeRegex(notificationType)
        }
        @Override
        protected void activatePushing() {
            adapter.helper.addNotificationListener(objectName, notificationListener, notificationFilter)
        }
        @Override
        protected void deactivatePushing() {
            adapter.helper.removeNotificationListener(objectName, notificationListener)
        }
        protected void addListener(NotificationListener listener) {
            pushedListeners.add(listener)
        }
        private void notifyListeners(Notification notif) {
            pushedListeners.each { NotificationListener listener ->
                println "about to call handleNotif"
                println "listener is "+listener
                try {
                    listener.handleNotification(notif, null)
                } catch (Throwable t) {
                t.printStackTrace();
                throw t
                }
                println "called handleNotif"
            }
        }
    }
    
    /** optional postProcessing will take the notification.userData
     * (its native type; casting to sensor's type is done on the return value of the closure) */
    public void subscribe(Sensor s, Closure postProcessing={it}) {
        pusher.addSensor(s, postProcessing);
    }
    
    /** optional postProcessing will take the notification
     */
    public void subscribe(NotificationListener listener) {
        pusher.addListener(listener);
    }
}
