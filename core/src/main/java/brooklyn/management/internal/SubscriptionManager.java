package brooklyn.management.internal;

import com.google.common.base.Predicate;

import brooklyn.entity.Entity;
import brooklyn.entity.EntitySummary;
import brooklyn.event.EventListener;
import brooklyn.event.Sensor;
import brooklyn.event.basic.SensorEvent;

public interface SubscriptionManager {
    /**
     * Subscribe to {@link Sensor} data changes and events on a given {@link Entity}, calling the {@link EventListener} when they occur.
     * 
     * @param entity The id of the entity
     * @param sensor The name of the sensor 
     * @param listener The listener to call when an event occurs
     * @see #subscribe(EntitySummary, Sensor, EventListener)
     */
    void subscribe(String entityId, String sensorName, EventListener listener);
    
    /**
     * @see #subscribe(String, String, EventListener)
     */
    void subscribe(EntitySummary entity, Sensor sensor, EventListener listener);
 
    void fire(SensorEvent<?> event);
 
//    void subscribe(Predicate<SensorEvent> filter, EventListener listener);
//    void subscribe(Predicate<Entity> entities, Predicate<SensorEvent> filter, EventListener listener);
}
