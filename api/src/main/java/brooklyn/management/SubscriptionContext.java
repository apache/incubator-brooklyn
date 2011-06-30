package brooklyn.management;

import java.util.Map;
import java.util.Set;

import brooklyn.entity.Entity;
import brooklyn.event.EventListener;
import brooklyn.event.Sensor;
import brooklyn.event.SensorEvent;

/**
 * This is the context through which an {@link Entity} can manage subscriptions.
 */
public interface SubscriptionContext {

    //REVIEW 1459 - class fleshed out, and preferred to manager access

    //REVIEW 1459 - getSubscriptionManager taken off API
    
    /**
     * As {@link SubscriptionManager#subscribe(Map, Entity, Sensor, EventListener)} with default subscription parameters for this context
     */
    <T> SubscriptionHandle subscribe(Map<String, Object> flags, Entity producer, Sensor<T> sensor, EventListener<T> listener);
    /** @see #subscribe(Map, Entity, Sensor, EventListener) */
    <T> SubscriptionHandle subscribe(Entity producer, Sensor<T> sensor, EventListener<T> listener);
    
    /** @see SubscriptionManager#unsubscribe(SubscriptionHandle) */
    boolean unsubscribe(SubscriptionHandle subscriptionId);
    
    /** causes all subscriptions to be deregistered
     * @return number of subscriptions removed */
    int unsubscribeAll();

    /** @see SubscriptionManager#publish(SensorEvent) */
    <T> void publish(SensorEvent<T> event);

    /** Return the subscriptions associated with this context */
    Set<SubscriptionHandle> getSubscriptions();

}