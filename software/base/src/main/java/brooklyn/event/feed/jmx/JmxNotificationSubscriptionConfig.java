package brooklyn.event.feed.jmx;

import javax.management.MalformedObjectNameException;
import javax.management.Notification;
import javax.management.NotificationFilter;
import javax.management.ObjectName;

import brooklyn.event.AttributeSensor;
import brooklyn.event.feed.FeedConfig;

import com.google.common.base.Function;
import com.google.common.base.Functions;

public class JmxNotificationSubscriptionConfig<T> extends FeedConfig<Object, T, JmxNotificationSubscriptionConfig<T>>{

    private ObjectName objectName;
    private NotificationFilter notificationFilter;
    private Function<Notification, T> onNotification;

    public JmxNotificationSubscriptionConfig(AttributeSensor<T> sensor) {
        super(sensor);
        onSuccess((Function)Functions.identity());
    }

    public JmxNotificationSubscriptionConfig(JmxNotificationSubscriptionConfig<T> other) {
        super(other);
        this.objectName = other.objectName;
        this.notificationFilter = other.notificationFilter;
        this.onNotification = other.onNotification;
    }

    public ObjectName getObjectName() {
        return objectName;
    }

    public NotificationFilter getNotificationFilter() {
        return notificationFilter;
    }
    
    public Function<Notification, T> getOnNotification() {
        return onNotification;
    }
    
    public JmxNotificationSubscriptionConfig<T> objectName(ObjectName val) {
        this.objectName = val; return this;
    }
    
    public JmxNotificationSubscriptionConfig<T> objectName(String val) {
        try {
            return objectName(new ObjectName(val));
        } catch (MalformedObjectNameException e) {
            throw new IllegalArgumentException("Invalid object name ("+val+")", e);
        }
    }
    
    public JmxNotificationSubscriptionConfig<T> notificationFilter(NotificationFilter val) {
        this.notificationFilter = val; return this;
    }

    /**
     * @deprecated since 0.6.0; use {@code notificationFilter(JmxNotificationFilters.matchesType(val))}
     * @see JmxNotificationFilters
     */
    public JmxNotificationSubscriptionConfig<T> notificationFilterByTypeRegex(String val) {
        this.notificationFilter = JmxNotificationFilters.matchesTypeRegex(val);
        return this;
    }
    
    public JmxNotificationSubscriptionConfig<T> onNotification(Function<Notification,T> val) {
        this.onNotification = val; return this;
    }
}
