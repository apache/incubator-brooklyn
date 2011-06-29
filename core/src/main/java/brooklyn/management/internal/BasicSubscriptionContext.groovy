package brooklyn.management.internal

import brooklyn.entity.Entity
import brooklyn.event.Sensor
import brooklyn.event.SensorEvent
import brooklyn.event.EventListener
import brooklyn.management.SubscriptionContext
import brooklyn.management.SubscriptionHandle
import brooklyn.management.SubscriptionManager

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
    	this.flags << flags;
    }
    
    public <T> SubscriptionHandle subscribe(Map<String, Object> newFlags=[:], Entity producer, Sensor<T> sensor, EventListener<T> listener) {
        Map f2 = [:]
        f2 << flags
        f2 << newFlags
        manager.subscribe(f2, producer, sensor, listener)
    }
    
    public boolean unsubscribe(SubscriptionHandle subscriptionId) {
        assert ((Subscription) subscriptionId).subscriber == subscriber
        manager.unsubscribe(subscriptionId)
    }

    /** @see SubscriptionManager#publish(SensorEvent) */
    public <T> void publish(SensorEvent<T> event) {
        manager.publish(event);
    }

    /** Return the subscriptions associated with this context */
    public Set<SubscriptionHandle> getSubscriptions() {
        return manager.getSubscriptionsBySubscriber(subscriber)
    }
    
}
