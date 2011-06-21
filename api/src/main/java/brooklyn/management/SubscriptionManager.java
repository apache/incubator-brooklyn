package brooklyn.management;

import brooklyn.entity.Entity;
import brooklyn.event.EventListener;
import brooklyn.event.Sensor;

public interface SubscriptionManager {
    /**
     * Subscribe to {@link Sensor} data changes and events on a given {@link Entity}, calling the {@link EventListener} when they occur.
     * 
     * The method returns an id which can be used to {@link #unsubscribe(long)} later.
     * 
     * @param interestedId The id of the entity receiving the data
     * @param producerId The id of the entity sending the data
     * @param sensorName The name of the sensor  on the entity
     * @param listener The listener to call when an event occurs
     * @return an id for this subscription
     * 
     * @see #unsubscribe(long)
     */
    <T> long subscribe(String interestedId, String producerId, String sensorName, EventListener<T> listener);
    
    /**
     * Unsubscribe the given subscription id.
     * 
     * @see #subscribe(String, String, String, EventListener)
     */
    void unsubscribe(long subscriptionId);
}
