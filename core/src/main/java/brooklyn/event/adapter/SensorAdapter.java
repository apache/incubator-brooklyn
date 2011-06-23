package brooklyn.event.adapter;

import brooklyn.entity.Entity;
import brooklyn.event.Sensor;

/**
 * Adapter to convert data from a sensor to events on an entity.
 * 
 * Implementations should provide a constructor that takes an {@link Entity} and any appropriate configuration, and should then convert
 * changes to subscribed {@link Sensor}s into {@link Entity#raiseEvent(Sensor, Object)} calls.
 */
public interface SensorAdapter {
    /** @see #subscribe(Sensor) */
    public void subscribe(String sensorName);
 
    /**
     * 
     */
    public <T> void subscribe(final Sensor<T> sensor);
    
    /** @see #poll(Sensor) */
    public <T> T poll(String sensorName);
 
    /**
     * 
     */
    public <T> T poll(Sensor<T> sensor);
}
