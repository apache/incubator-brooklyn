package brooklyn.event.basic;

import groovy.lang.Closure;

import java.util.List;

import brooklyn.entity.Entity;
import brooklyn.event.Sensor;

import com.google.common.base.Predicate;

public class EventFilters {
    private EventFilters() {}
    
    public static Predicate<SensorEvent<?>> sensorName(final String name) {
        return new Predicate<SensorEvent<?>>() {
            public boolean apply(SensorEvent<?> event) {
                Sensor<?> sensor = event.getSensor();
                return sensor.getName().equals(name);
            }
        };
    }
    
    public static Predicate<SensorEvent<?>> sensorNamePart(final List<String> parts) {
        return new Predicate<SensorEvent<?>>() {
            public boolean apply(SensorEvent<?> event) {
                Sensor<?> sensor = event.getSensor();
                List<String> sensorParts = sensor.getNameParts();
                if (parts.size() > sensorParts.size()) return false;
                for (int i = 0; i < parts.size(); i++) {
                    if (!parts.get(i).equals(sensorParts.get(i))) return false;
                }
                return true;
            }
        };
    }
 
    public static Predicate<SensorEvent<?>> sensorValue(final Object value) {
        return new Predicate<SensorEvent<?>>() {
            public boolean apply(SensorEvent<?> event) {
                return event.getValue().equals(value);
            }
        };
    }
    
    public static Predicate<SensorEvent<?>> sensor(final Closure<Boolean> expr) {
        return new Predicate<SensorEvent<?>>() {
            public boolean apply(SensorEvent<?> event) {
                return expr.call(event.getValue());
            }
        };
    }
    
    public static Predicate<Entity> entityId(final String id) {
        return new Predicate<Entity>() {
            public boolean apply(Entity entity) {
                return entity.getId().equals(id);
            }
        };
    }
    
    public static Predicate<SensorEvent<?>> all() {
        return new Predicate<SensorEvent<?>>() {
            public boolean apply(SensorEvent<?> sensor) {
                return true;
            }
        };
    }
}
