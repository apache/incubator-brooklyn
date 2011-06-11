package org.overpaas.event;

import org.overpaas.types.Sensor;
import org.overpaas.types.SensorEvent;

public class EventFilters {
    private EventFilters() {}
    
    public static <T> EventFilter<T> sensorName(final String sensorName) {
        return new EventFilter<T>() {
            public boolean apply(SensorEvent<T> event) {
                Sensor<T> sensor = event.getSensor();
                return sensor.name.equals(sensorName);
            }
        };
    }
    
    public static <T> EventFilter<T> sensorValue(final T sensorValue) {
        return new EventFilter<T>() {
            public boolean apply(SensorEvent<T> event) {
                return event.get().equals(sensorValue);
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
