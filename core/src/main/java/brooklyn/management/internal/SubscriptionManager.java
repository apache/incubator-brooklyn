package brooklyn.management.internal;

import brooklyn.entity.Entity;
import brooklyn.entity.EntitySummary;
import brooklyn.event.EventListener;
import brooklyn.event.Sensor;

public interface SubscriptionManager {
    /**
     * Subscribe to {@link Sensor} data changes and events on a given {@link Entity}, calling the {@link EventListener} when they occur.
     * 
     * The method returns an id which can be used to {@link #unsubscribe(long)} later.
     * 
     * @param interestedId The id of the entity receiving the data
     * @param sourceId The id of the entity sending the data
     * @param sensorName The name of the sensor  on the entity
     * @param listener The listener to call when an event occurs
     * @return an id for this subscription
     * 
     * @see #subscribe(EntitySummary, Sensor, EventListener)
     * @see #unsubscribe(long)
     */
    <T> long subscribe(String interestedId, String sourceId, String sensorName, EventListener<T> listener);
    
    /**
     * @see #subscribe(String, String, String, EventListener)
     * @see #unsubscribe(long)
     */
    <T> long subscribe(EntitySummary interestedEntity, EntitySummary sourceEntity, Sensor<T> sensor, EventListener<T> listener);
    
    /**
     * Unsubscribe the given subscription id.
     * 
     * @see #subscribe(EntitySummary, Sensor, EventListener)
     * @see #subscribe(String, String, String, EventListener)
     */
    void unsubscribe(long subscriptionId);
}
