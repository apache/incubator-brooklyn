package brooklyn.management.internal;

import java.util.Collection;
import java.util.Collections;

import brooklyn.entity.Entity;
import brooklyn.event.Sensor;
import brooklyn.event.SensorEventListener;
import brooklyn.management.SubscriptionContext;
import brooklyn.management.SubscriptionHandle;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;

/**
 * Tracks subscriptions associated that are registered with particular entities. Gives utilities for unsubscribing from all
 * subscriptions on a given entity, etc.
 */
public class SubscriptionTracker {

    protected transient SubscriptionContext subscription;
    
    private SetMultimap<Entity, SubscriptionHandle> subscriptions = HashMultimap.create();

    public SubscriptionTracker(SubscriptionContext subscriptionContext) {
        this.subscription = subscriptionContext;
    }
    
    /** @see SubscriptionContext#subscribe(Entity, Sensor, SensorEventListener) */
    public <T> SubscriptionHandle subscribe(Entity producer, Sensor<T> sensor, SensorEventListener<? super T> listener) {
        SubscriptionHandle handle = subscription.subscribe(producer, sensor, listener);
        subscriptions.put(producer, handle);
        return handle;
    }
    
    /** @see SubscriptionContext#subscribeToChildren(Entity, Sensor, SensorEventListener) */
    public <T> SubscriptionHandle subscribeToChildren(Entity parent, Sensor<T> sensor, SensorEventListener<? super T> listener) {
        SubscriptionHandle handle = subscription.subscribeToChildren(parent, sensor, listener);
        subscriptions.put(parent, handle);
        return handle;
    }

    /** @see SubscriptionContext#subscribeToMembers(Entity, Sensor, SensorEventListener) */
    public <T> SubscriptionHandle subscribeToMembers(Entity parent, Sensor<T> sensor, SensorEventListener<? super T> listener) {
        SubscriptionHandle handle = subscription.subscribeToMembers(parent, sensor, listener);
        subscriptions.put(parent, handle);
        return handle;
    }    

    /**
     * Unsubscribes the given producer.
     *
     * @see SubscriptionContext#unsubscribe(SubscriptionHandle)
     */
    public boolean unsubscribe(Entity producer) {
        Collection<SubscriptionHandle> handles = subscriptions.removeAll(producer);
        if (handles != null) {
            for (SubscriptionHandle handle : handles) {
                subscription.unsubscribe(handle);
            }
            return true;
        }
        return false;
    }

    /**
    * Unsubscribes the given producer.
    *
    * @see SubscriptionContext#unsubscribe(SubscriptionHandle)
    */
   public boolean unsubscribe(Entity producer, SubscriptionHandle handle) {
       subscriptions.remove(producer, handle);
       return subscription.unsubscribe(handle);
   }

    /**
    * @return an ordered list of all subscription handles
    */
   public Collection<SubscriptionHandle> getAllSubscriptions() {
       return Collections.unmodifiableCollection(subscriptions.values());
   }
   
   public void unsubscribeAll() {
       for (SubscriptionHandle s: subscriptions.values())
           subscription.unsubscribe(s);
       subscriptions.clear();
   }
}
