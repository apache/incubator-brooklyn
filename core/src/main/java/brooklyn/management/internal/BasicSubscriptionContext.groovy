package brooklyn.management.internal

import brooklyn.entity.Entity
import brooklyn.entity.Group
import brooklyn.event.SensorEventListener
import brooklyn.event.Sensor
import brooklyn.event.SensorEvent
import brooklyn.management.SubscriptionContext
import brooklyn.management.SubscriptionHandle
import brooklyn.management.SubscriptionManager

import com.google.common.base.Preconditions

/**
 * A {@link SubscriptionContext} for an entitiy or other user of a {@link SubscriptionManager}.
 */
public class BasicSubscriptionContext implements SubscriptionContext {
    private SubscriptionManager manager;
    private Object subscriber;
    private Map flags;
    
    public BasicSubscriptionContext(Map<String, Object> flags=[:], SubscriptionManager manager, Object subscriber) {
    	this.manager = manager;
        this.subscriber = subscriber;
        this.flags = [subscriber:subscriber]
    	if (flags!=null) this.flags << flags;
    }

    public <T> SubscriptionHandle subscribe(Map<String, Object> newFlags=[:], Entity producer, Sensor<T> sensor, Closure c) {
        subscribe(newFlags, producer, sensor, c as SensorEventListener)        
    }

    public <T> SubscriptionHandle subscribe(Map<String, Object> newFlags=[:], Entity producer, Sensor<T> sensor, SensorEventListener<? super T> listener) {
        Map subscriptionFlags = [:]
        subscriptionFlags << flags
        if (newFlags) subscriptionFlags << newFlags
        manager.subscribe(subscriptionFlags, producer, sensor, listener)
    }
    
    public <T> SubscriptionHandle subscribeToChildren(Map<String, Object> newFlags=[:], Entity parent, Sensor<T> sensor, Closure c) {
        subscribeToChildren(newFlags, parent, sensor, c as SensorEventListener)        
    }

    public <T> SubscriptionHandle subscribeToChildren(Map<String, Object> newFlags=[:], Entity parent, Sensor<T> sensor, SensorEventListener<? super T> listener) {
        Map subscriptionFlags = [:]
        subscriptionFlags << flags
        if (newFlags) subscriptionFlags << newFlags
        manager.subscribeToChildren(subscriptionFlags, parent, sensor, listener)
    }
 
    public <T> SubscriptionHandle subscribeToMembers(Map<String, Object> newFlags=[:], Entity parent, Sensor<T> sensor, Closure c) {
        subscribeToMembers(newFlags, parent, sensor, c as SensorEventListener)
    }

    public <T> SubscriptionHandle subscribeToMembers(Map<String, Object> newFlags=[:], Entity parent, Sensor<T> sensor, SensorEventListener<? super T> listener) {
        Map subscriptionFlags = [:]
        subscriptionFlags << flags
        if (newFlags) subscriptionFlags << newFlags
        manager.subscribeToMembers(subscriptionFlags, parent, sensor, listener)
    }

    public boolean unsubscribe(SubscriptionHandle subscriptionId) {
        Preconditions.checkNotNull subscriptionId, "subscriptionId should not be null"
        Preconditions.checkArgument(subscriber == ((Subscription) subscriptionId).subscriber, "The subscriptionId is for a different $subscriber")
        manager.unsubscribe(subscriptionId)
    }

    /** @see SubscriptionManager#publish(SensorEvent) */
    public <T> void publish(SensorEvent<T> event) {
        manager.publish(event);
    }

    /** Return the subscriptions associated with this context */
    public Set<SubscriptionHandle> getSubscriptions() {
        manager.getSubscriptionsForSubscriber(subscriber)
    }

    public int unsubscribeAll() {
        int count = 0;
        getSubscriptions().each { count++; boolean result = unsubscribe(it); assert result }
        count
    }
}
