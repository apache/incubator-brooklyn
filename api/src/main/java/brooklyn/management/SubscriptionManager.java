package brooklyn.management;

import java.util.Map;
import java.util.Set;

import brooklyn.entity.Entity;
import brooklyn.event.SensorEvent;
import brooklyn.event.EventListener;
import brooklyn.event.Sensor;

/**
 * The mangement interface for subscriptions.
 * 
 * Different implementations will handle entities subscribing and unsubscribing to {@link SensorEvent}s
 * and their delivery.
 * 
 * @see SubscriptionContext
 */
public interface SubscriptionManager {
    
    /**
     * Subscribe to {@link Sensor} data changes and events on a given {@link Entity}, supplying the {@link EventListener} to invoke when they occur
     * 
     * The method returns an id which can be used to {@link #unsubscribe(long)} later.
     * 
     * @param <T> type of 
     * @param flags parameters to be associated with the subscription (optional), including:
     * <li>subscriber - object to identify the subscriber (e.g. entity, or console session uid); 
     * listener callback is in-order single-threaded, synched on this object (if supplied and non-null)
     * <li><i>in future</i> - control parameters for the subscription (period, minimum delta for updates, etc)
     * @param producer entity to listen to
     * @param sensor sensor channel of events to listen to
     * @param listener callback to invoke when an event occurs
     * @return an id for this subscription
     * 
     * @see #unsubscribe(Object)
     */
    <T> SubscriptionHandle subscribe(Map<String, Object> flags, Entity producer, Sensor<T> sensor, EventListener<T> listener);
    /** @see #subscribe(Map, Entity, Sensor, EventListener) */
    <T> SubscriptionHandle subscribe(Entity producer, Sensor<T> sensor, EventListener<T> listener);
    //REVIEW 1459 - parameters changed (from strings, optional tags e.g. subscriber)
    
    //REVIEW 1459 - changed nominal type of subscription ID to Object (may use long but that is internal detail)
    /**
     * Unsubscribe the given subscription id.
     * 
     * @return true if such a subscription was present (and it will now be removed)
     * @see #subscribe(Map, Entity, Sensor, EventListener)
     */
    boolean unsubscribe(SubscriptionHandle subscriptionId);

    /**
     * Deliver a {@link SensorEvent} to all subscribed listeners.
     */
    <T> void publish(SensorEvent<T> event);

    /** Return the subscriptions requested by a given subscriber */
    Set<SubscriptionHandle> getSubscriptionsForSubscriber(Object subscriber);
    /** Return the subscriptions on a given source-sensor pair */
    Set<SubscriptionHandle> getSubscriptionsForEntitySensor(Entity source, Sensor<?> sensor);
    
}
