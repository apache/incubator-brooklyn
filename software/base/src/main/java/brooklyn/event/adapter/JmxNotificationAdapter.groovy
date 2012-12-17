package brooklyn.event.adapter

import groovy.lang.Closure

import java.util.Map
import java.util.concurrent.CopyOnWriteArrayList

import javax.management.Notification
import javax.management.NotificationFilter
import javax.management.NotificationListener
import javax.management.ObjectName

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.event.Sensor

/** 
 * Adapter that subscribes to a JMX notification.
 * 
 * @see {@link JmxSensorAdapter} for recommended way of using this
 */
class JmxNotificationAdapter extends AbstractSensorAdapter {
    
    private static final Logger LOG = LoggerFactory.getLogger(JmxNotificationAdapter.class)
    
    final JmxSensorAdapter adapter
    final ObjectName objectName
    final String notificationType
    final NotificationPushHelper pusher
    
    public JmxNotificationAdapter(Map flags=[:], JmxSensorAdapter adapter, ObjectName objectName, String notificationType) {
        super(flags);
        this.adapter = adapter;
        //FIXME adapter.addActivationLifecycleListeners({activateAdapter()},{deactivateAdapter()});
        pusher = new NotificationPushHelper(adapter, objectName, notificationType);
        pusher.init();
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
            this.notificationFilter = notificationType ? JmxNotificationFilters.matchesTypeRegex(notificationType) : null
            this.notificationListener = { Notification notif, Object callback ->
                    if (notificationFilter == null || notificationFilter.isNotificationEnabled(notif)) {
                        def wrappedVal = new SingleValueResponseContext(value:notif.getUserData())
                        onPush(wrappedVal)
                        notifyListeners(notif) 
                    } } as NotificationListener
        }
        @Override
        protected void activatePushing() {
            if (LOG.isTraceEnabled()) LOG.trace("Activating notification listener on $objectName with filter '$notificationFilter', listeners $pushedListeners")
            adapter.helper.addNotificationListener(objectName, notificationListener)
        }
        @Override
        protected void deactivatePushing() {
            if (LOG.isTraceEnabled()) LOG.trace("Deactivating notification listener on $objectName with filter '$notificationFilter'")
            adapter?.helper?.removeNotificationListener(objectName, notificationListener)
        }
        protected void addListener(NotificationListener listener) {
            pushedListeners.add(listener)
        }
        private void notifyListeners(Notification notif) {
            if (LOG.isTraceEnabled()) LOG.trace("Received notification {}; notifiying listeners {}", notif, pushedListeners)
            pushedListeners.each { NotificationListener listener ->
                try {
                    listener.handleNotification(notif, null)
                } catch (Exception t) {
                    LOG.error("Error in listener $listener handling notification $notif", t)
                }
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
    
    @Override
    protected void activateAdapter() {
        super.activateAdapter();
        adapter.checkObjectNameExists(objectName);
    }

}
