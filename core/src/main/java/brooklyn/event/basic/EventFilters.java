package brooklyn.event.basic;

import groovy.lang.Closure;

import java.util.List;

import brooklyn.entity.Entity;
import brooklyn.event.Sensor;

import com.google.common.base.Predicate;

public class EventFilters {
    private EventFilters() {}
    
    public static Predicate<BasicSensorEvent<?>> sensorName(final String name) {
        return new Predicate<BasicSensorEvent<?>>() {
            public boolean apply(BasicSensorEvent<?> event) {
                Sensor<?> sensor = event.getSensor();
                return sensor.getName().equals(name);
            }
        };
    }
    
    public static Predicate<BasicSensorEvent<?>> sensorNamePart(final List<String> parts) {
        return new Predicate<BasicSensorEvent<?>>() {
            public boolean apply(BasicSensorEvent<?> event) {
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
 
    public static Predicate<BasicSensorEvent<?>> sensorValue(final Object value) {
        return new Predicate<BasicSensorEvent<?>>() {
            public boolean apply(BasicSensorEvent<?> event) {
                return event.getValue().equals(value);
            }
        };
    }
    
    public static Predicate<BasicSensorEvent<?>> sensor(final Closure<Boolean> expr) {
        return new Predicate<BasicSensorEvent<?>>() {
            public boolean apply(BasicSensorEvent<?> event) {
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
    
    public static Predicate<BasicSensorEvent<?>> all() {
        return new Predicate<BasicSensorEvent<?>>() {
            public boolean apply(BasicSensorEvent<?> sensor) {
                return true;
            }
        };
    }
}
