package brooklyn.event.basic;

import groovy.lang.Closure;
import brooklyn.event.Sensor;

public class EventFilters {
    private EventFilters() {}
    
    public static <T> EventFilter<T> sensorName(final String name) {
        return new EventFilter<T>() {
            public boolean apply(SensorEvent<T> event) {
                Sensor<T> sensor = event.getSensor();
                return sensor.getName().equals(name);
            }
        };
    }
 
    public static <T> EventFilter<T> sensorValue(final T value) {
        return new EventFilter<T>() {
            public boolean apply(SensorEvent<T> event) {
                return event.getValue().equals(value);
            }
        };
    }
    
    public static <T> EventFilter<T> sensor(final Closure<Boolean> expr) {
        return new EventFilter<T>() {
            public boolean apply(SensorEvent<T> event) {
                return expr.call(event.getValue());
            }
        };
    }
    
    public static <T> EventFilter<T> entityId(final String id) {
        return new EventFilter<T>() {
            public boolean apply(SensorEvent<T> event) {
                return event.getSource().getId().equals(id);
            }
        };
    }
    
    public static <T> EventFilter<T> all() {
        return new EventFilter<T>() {
            public boolean apply(SensorEvent<T> sensor) {
                return true;
            }
        };
    }
}
