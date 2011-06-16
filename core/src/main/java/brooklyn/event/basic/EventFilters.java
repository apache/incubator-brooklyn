package brooklyn.event.basic;

import java.util.Collection;
import java.util.List;

import com.google.common.base.Predicate;

import groovy.lang.Closure;
import brooklyn.event.Sensor;

public class EventFilters {
    private EventFilters() {}
    
    public static <T> Predicate<SensorEvent<T>> sensorName(final String name) {
        return new Predicate<SensorEvent<T>>() {
            public boolean apply(SensorEvent<T> event) {
                Sensor<T> sensor = event.getSensor();
                return sensor.getName().equals(name);
            }
        };
    }
    
    public static <T> Predicate<SensorEvent<T>> sensorNamePart(final List<String> parts) {
        return new Predicate<SensorEvent<T>>() {
            public boolean apply(SensorEvent<T> event) {
                Sensor<T> sensor = event.getSensor();
                List<String> sensorParts = sensor.getNameParts();
                if (parts.size() > sensorParts.size()) return false;
                for (int i = 0; i < parts.size(); i++) {
                    if (!parts.get(i).equals(sensorParts.get(i))) return false;
                }
                return true;
            }
        };
    }
 
    public static <T> Predicate<SensorEvent<T>> sensorValue(final T value) {
        return new Predicate<SensorEvent<T>>() {
            public boolean apply(SensorEvent<T> event) {
                return event.getValue().equals(value);
            }
        };
    }
    
    public static <T> Predicate<SensorEvent<T>> sensor(final Closure<Boolean> expr) {
        return new Predicate<SensorEvent<T>>() {
            public boolean apply(SensorEvent<T> event) {
                return expr.call(event.getValue());
            }
        };
    }
    
    public static <T> Predicate<SensorEvent<T>> entityId(final String id) {
        return new Predicate<SensorEvent<T>>() {
            public boolean apply(SensorEvent<T> event) {
                return event.getSource().getId().equals(id);
            }
        };
    }
    
    public static <T> Predicate<SensorEvent<T>> all() {
        return new Predicate<SensorEvent<T>>() {
            public boolean apply(SensorEvent<T> sensor) {
                return true;
            }
        };
    }
}
