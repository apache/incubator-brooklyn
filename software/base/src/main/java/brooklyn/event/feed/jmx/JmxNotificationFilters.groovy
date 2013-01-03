package brooklyn.event.feed.jmx;

import javax.management.Notification
import javax.management.NotificationFilter

public class JmxNotificationFilters {

    private JmxNotificationFilters() {} // instead use static utility methods
    
    public static NotificationFilter matchesType(String type) {
        return { Notification notif -> return type.equals(notif.getType()) } as NotificationFilter
    }
    
    public static NotificationFilter matchesTypeRegex(String typeRegex) {
        return { Notification notif -> return notif.getType().matches(typeRegex) } as NotificationFilter
    }
}
