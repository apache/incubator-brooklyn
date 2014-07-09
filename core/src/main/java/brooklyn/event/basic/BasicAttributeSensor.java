package brooklyn.event.basic;

import brooklyn.event.AttributeSensor;
import brooklyn.event.Sensor;

import com.google.common.reflect.TypeToken;

/**
 * A {@link Sensor} describing an attribute change.
 */
public class BasicAttributeSensor<T> extends BasicSensor<T> implements AttributeSensor<T> {
    private static final long serialVersionUID = -2493209215974820300L;

    public BasicAttributeSensor(Class<T> type, String name) {
        this(type, name, name);
    }
    
    public BasicAttributeSensor(Class<T> type, String name, String description) {
        super(type, name, description);
    }
    
    public BasicAttributeSensor(TypeToken<T> typeToken, String name) {
        this(typeToken, name, name);
    }
    
    public BasicAttributeSensor(TypeToken<T> typeToken, String name, String description) {
        super(typeToken, name, description);
    }
    
}
