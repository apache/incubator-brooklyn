package brooklyn.management.internal;

import java.util.concurrent.ConcurrentHashMap;

import java.util.Map
import java.util.Set
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

import brooklyn.entity.Entity
import brooklyn.entity.Group
import brooklyn.event.EventListener
import brooklyn.event.Sensor
import brooklyn.event.SensorEvent
import brooklyn.management.ExecutionManager
import brooklyn.management.SubscriptionHandle
import brooklyn.management.SubscriptionManager
import brooklyn.util.internal.LanguageUtils
import brooklyn.util.task.BasicExecutionManager
import brooklyn.util.task.SingleThreadedExecution

import com.google.common.base.Predicate

/**
 * A {@link SubscriptionManager} that stores subscription details locally.
 */
public class LocalSubscriptionManager implements SubscriptionManager {
    static String makeEntitySensorToken(Entity e, Sensor<?> s) {
        return (e ? e.id :  "*")+":"+(s ? s.name : "*")
    }
 
    static String makeEntitySensorToken(SensorEvent<?> se) {
        makeEntitySensorToken(se.source, se.sensor)
    }

    protected final ExecutionManager em;
    
    protected ConcurrentMap<String, Subscription> allSubscriptions = new ConcurrentHashMap<String, Subscription>();
    protected ConcurrentMap<Object, Set<Subscription>> subscriptionsBySubscriber = new ConcurrentHashMap<Object, Set<Subscription>>()
    protected ConcurrentMap<Object, Set<Subscription>> subscriptionsByToken = new ConcurrentHashMap<Object, Set<Subscription>>()
  
    public LocalSubscriptionManager(ExecutionManager m) {
        this.em = m
    }
      
    /** @see SubscriptionManager#subscribe(Map, Entity, Sensor, EventListener) */
    public <T> SubscriptionHandle subscribe(Entity producer, Sensor<T> sensor, EventListener<T> listener) {
        subscribe([:], producer, sensor, listener)
    }
 
    /**
     * This implementation handles the following flags, in addition to those described in the {@link SubscriptionManager}
     * interface:
     * <ul>
     * <li>subscriberExecutionManagerTag - a tag to pass to execution manager (without setting any execution semantics / TaskPreprocessor);
     *      if not supplied and there is a subscriber, this will be inferred from the subscriber and set up with SingleThreadedExecution
     *      (supply this flag with value null to prevent any task preprocessor from being set)
     * <li>eventFilter - a Predicate&lt;SensorEvent&gt; instance to filter what events are delivered
     * </ul>
     * 
     * @see SubscriptionManager#subscribe(Map, Entity, Sensor, EventListener)
     */
    public synchronized <T> SubscriptionHandle subscribe(Map<String, Object> flags, Entity producer, Sensor<T> sensor, EventListener<T> listener) {
        Subscription s = new Subscription(producer:producer, sensor:sensor, listener:listener)
        s.subscriber = flags.remove("subscriber")
        if (flags.containsKey("subscriberExecutionManagerTag")) {
            s.subscriberExecutionManagerTag = flags.remove("subscriberExecutionManagerTag");
            s.subscriberExecutionManagerTagSupplied = true
        } else {
            s.subscriberExecutionManagerTag = 
                s.subscriber in Entity ? "subscription-delivery-entity["+s.subscriber.id+"]" : 
                s.subscriber in String ? "subscription-delivery-string["+s.subscriber+"]" : 
                s!=null ? "subscription-delivery-object["+s.subscriber+"]" : null;
            s.subscriberExecutionManagerTagSupplied = false
        }
        s.eventFilter = flags.remove("eventFilter")
        s.flags = flags
        
        allSubscriptions.put(s.id, s)
        LanguageUtils.addToMapOfSets(subscriptionsByToken, makeEntitySensorToken(s.producer, s.sensor), s);
        if (s.subscriber!=null) {
            LanguageUtils.addToMapOfSets(subscriptionsBySubscriber, s.subscriber, s);
        }
        if (!s.subscriberExecutionManagerTagSupplied && s.subscriberExecutionManagerTag!=null) {
            ((BasicExecutionManager) em).setTaskPreprocessorForTag(s.subscriberExecutionManagerTag, SingleThreadedExecution.class);
        }
        s
    }
  
    /** @see SubscriptionManager#subscribeToChildren(Map, Entity, Sensor, EventListener) */
    public <T> SubscriptionHandle subscribeToChildren(Entity parent, Sensor<T> sensor, EventListener<T> listener) {
        subscribeToChildren([:], parent, sensor, listener)
    }

    /** @see SubscriptionManager#subscribe(Map, Entity, Sensor, EventListener) */
    public synchronized <T> SubscriptionHandle subscribeToChildren(Map<String, Object> flags, final Entity parent, Sensor<T> sensor, EventListener<T> listener) {
        Predicate<SensorEvent<T>> eventFilter = new Predicate<SensorEvent<?>>() {
            public boolean apply(SensorEvent<?> input) {
                return input.source.isAncestor(parent)
            }
        }
        flags.put("eventFilter", eventFilter)
        flags.put("subscriber", parent.id)
        subscribe(flags, null, sensor, listener)
    }

    /**
     * Unsubscribe the given subscription id.
     *
     * @see #subscribe(Map, Entity, Sensor, EventListener)
     */
    public synchronized boolean unsubscribe(SubscriptionHandle sh) {
        Subscription s = sh
        boolean b1 = allSubscriptions.remove(s.id)
        boolean b2 = LanguageUtils.removeFromMapOfCollections(subscriptionsByToken, makeEntitySensorToken(s.producer, s.sensor), s);
        assert b1==b2
        if (s.subscriber!=null) {
            boolean b3 = LanguageUtils.removeFromMapOfCollections(subscriptionsBySubscriber, s.subscriber, s);
            assert b3 == b2
        }
        
        if (!subscriptionsBySubscriber.get(s.subscriber) && !s.subscriberExecutionManagerTagSupplied && s.subscriberExecutionManagerTag!=null) {
            //if subscriber has gone away forget about his task; but check in synch block to ensure setTaskPreprocessor call above will win in any race
            if (!subscriptionsBySubscriber.get(s.subscriber))
                ((BasicExecutionManager)em).clearTaskPreprocessorForTag(s.subscriberExecutionManagerTag);
        }

        ((BasicExecutionManager) em).setTaskPreprocessorForTag(s.subscriberExecutionManagerTag, SingleThreadedExecution.class);
        return b1
    }

    public Set<SubscriptionHandle> getSubscriptionsForSubscriber(Object subscriber) {
        return subscriptionsBySubscriber.get(subscriber) ?: Collections.emptySet()
    }

    public Set<SubscriptionHandle> getSubscriptionsForEntitySensor(Entity source, Sensor sensor) {
        Set<SubscriptionHandle> subscriptions = []
        subscriptions.addAll(subscriptionsByToken.get(makeEntitySensorToken(source, sensor)) ?: Collections.emptySet())
        subscriptions.addAll(subscriptionsByToken.get(makeEntitySensorToken(null, sensor)) ?: Collections.emptySet())
        subscriptions.addAll(subscriptionsByToken.get(makeEntitySensorToken(source, null)) ?: Collections.emptySet())
        subscriptions.addAll(subscriptionsByToken.get(makeEntitySensorToken(null, null)) ?: Collections.emptySet())
        subscriptions
    }

    public <T> void publish(SensorEvent<T> event) {
        // REVIEW 1459 - execution
        
        // delivery in parallel/background, using execution manager
        
        // subscriptions, should define SingleThreadedExecution for any subscriber ID tag
        // in order to ensure callbacks are invoked in the order they are submitted
        // (recommend exactly one per subscription to prevent deadlock)
        // this is done with:
        // em.setTaskPreprocessorForTag(subscriberId, SingleThreadedExecution.class);
        
        //note, generating the notifications must be done in the calling thread to preserve order
        //e.g. emit(A); emit(B); should cause onEvent(A); onEvent(B) in that order
        Set<Subscription> subs = getSubscriptionsForEntitySensor(event.source, event.sensor);
        if (subs) {
            for (Subscription s in subs) {
                if (s.eventFilter!=null && !s.eventFilter.apply(event))
                    continue;
                em.submit(tags: s.subscriberExecutionManagerTag, { s.listener.onEvent(event) })
            }
        }
    }
}

class Subscription<T> implements SubscriptionHandle {
    private String id = LanguageUtils.newUid();
    
    public Object subscriber;
    public Object subscriberExecutionManagerTag;
    /** whether the tag was supplied by user, in which case we should not clear execution semantics */
    public boolean subscriberExecutionManagerTagSupplied;
    public Entity producer;
    public Sensor<T> sensor;
    public EventListener<T> listener;
    public Map<String,Object> flags;
    public Predicate<SensorEvent<T>> eventFilter;

    @Override
    public boolean equals(Object other) {
        return (other instanceof Subscription && ((Subscription)other).id==id)
    }

    @Override
    public int hashCode() {
        return id.hashCode()
    }
    
    @Override
    public String toString() {
        return "Subscription[$id;$subscriber@"+LocalSubscriptionManager.makeEntitySensorToken(producer,sensor)+"]"
    }
}
