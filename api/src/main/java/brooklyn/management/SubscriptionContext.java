package brooklyn.management;

import java.util.Map;
import java.util.Set;

import brooklyn.entity.Entity;
import brooklyn.event.SensorEventListener;
import brooklyn.event.Sensor;
import brooklyn.event.SensorEvent;

/**
 * This is the context through which an {@link Entity} can manage subscriptions.
 */
public interface SubscriptionContext {
    /**
     * As {@link SubscriptionManager#subscribe(Map, Entity, Sensor, SensorEventListener)} with default subscription parameters for this context
     */
    <T> SubscriptionHandle subscribe(Map<String, Object> flags, Entity producer, Sensor<T> sensor, SensorEventListener<? super T> listener);
 
    /** @see #subscribe(Map, Entity, Sensor, SensorEventListener) */
    <T> SubscriptionHandle subscribe(Entity producer, Sensor<T> sensor, SensorEventListener<? super T> listener);
    
    /** @see #subscribe(Map, Entity, Sensor, SensorEventListener) */
    <T> SubscriptionHandle subscribeToChildren(Map<String, Object> flags, Entity parent, Sensor<T> sensor, SensorEventListener<? super T> listener);
 
    /** @see #subscribe(Map, Entity, Sensor, SensorEventListener) */
    <T> SubscriptionHandle subscribeToChildren(Entity parent, Sensor<T> sensor, SensorEventListener<? super T> listener);
    
    /** @see #subscribe(Map, Entity, Sensor, SensorEventListener) */
    <T> SubscriptionHandle subscribeToMembers(Map<String, Object> flags, Entity parent, Sensor<T> sensor, SensorEventListener<? super T> listener);
 
    /** @see #subscribe(Map, Entity, Sensor, SensorEventListener) */
    <T> SubscriptionHandle subscribeToMembers(Entity parent, Sensor<T> sensor, SensorEventListener<? super T> listener);
    
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