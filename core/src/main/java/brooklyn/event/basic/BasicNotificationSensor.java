package brooklyn.event.basic;

import brooklyn.event.Sensor;

/**
 * A {@link Sensor} used to notify subscribers about events.
 */
public class BasicNotificationSensor<T> extends BasicSensor<T> {
    private static final long serialVersionUID = -7670909215973264600L;

    public BasicNotificationSensor(Class<T> type, String name) {
        this(type, name, name);
    }
    
    public BasicNotificationSensor(Class<T> type, String name, String description) {
        super(type, name, description);
    }
}
