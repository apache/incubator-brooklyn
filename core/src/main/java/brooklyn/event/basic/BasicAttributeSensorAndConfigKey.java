package brooklyn.event.basic;

import brooklyn.entity.Entity;

/**
 * A {@link Sensor} describing an attribute that can be configured with a default value.
 *
 * The {@link ConfigKey} has the same type, name and description as the sensor,
 * and is typically used to populate the sensor's value at runtime.
 */
public class BasicAttributeSensorAndConfigKey<T> extends AttributeSensorAndConfigKey<T,T> {
    
    public BasicAttributeSensorAndConfigKey(Class<T> type, String name) {
        this(type, name, name, null);
    }
    public BasicAttributeSensorAndConfigKey(Class<T> type, String name, String description) {
        this(type, name, description, null);
    }

    public BasicAttributeSensorAndConfigKey(Class<T> type, String name, String description, T defaultValue) {
        super(type, type, name, description, defaultValue);
    }

    public BasicAttributeSensorAndConfigKey(BasicAttributeSensorAndConfigKey<T> orig, T defaultValue) {
        super(orig, defaultValue);
    }
    
    protected T convertConfigToSensor(T value, Entity entity) { return value; }
}
