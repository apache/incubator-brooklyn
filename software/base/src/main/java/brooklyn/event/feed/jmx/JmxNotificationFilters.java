package brooklyn.event.feed.jmx;

import javax.management.Notification;
import javax.management.NotificationFilter;

public class JmxNotificationFilters {

    private JmxNotificationFilters() {} // instead use static utility methods
    
    @SuppressWarnings("serial")
    public static NotificationFilter matchesType(final String type) {
        return new NotificationFilter() {
            @Override public boolean isNotificationEnabled(Notification notif) {
                return type.equals(notif.getType());
            }
        };
    }
    
    @SuppressWarnings("serial")
    public static NotificationFilter matchesTypeRegex(final String typeRegex) {
        return new NotificationFilter() {
            @Override public boolean isNotificationEnabled(Notification notif) {
                return notif.getType().matches(typeRegex);
            }
        };
    }
}
