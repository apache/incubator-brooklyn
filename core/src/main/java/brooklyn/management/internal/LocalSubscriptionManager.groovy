package brooklyn.management.internal;

import java.util.concurrent.ConcurrentHashMap;

import java.util.Map;
import java.util.Set;

import brooklyn.event.Sensor;
import brooklyn.management.SubscriptionHandle;


import java.util.List
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock

import brooklyn.entity.Entity
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.event.SensorEvent
import brooklyn.event.EventListener
import brooklyn.event.basic.EventFilters
import brooklyn.event.basic.BasicSensorEvent
import brooklyn.management.ExecutionManager;
import brooklyn.management.SubscriptionManager
import brooklyn.util.internal.LanguageUtils;
import brooklyn.util.task.BasicExecutionManager;
import brooklyn.util.task.SingleThreadedExecution;

import com.google.common.base.Objects
import com.google.common.base.Predicate

/**
 * A {@link SubscriptionManager} that stores subscription details locally.
 */
public class LocalSubscriptionManager implements SubscriptionManager {
    
    static Object makeEntitySensorToken(Entity e, Sensor<?> s) {
        return e?.id+":"+s?.name
    }
    static Object makeEntitySensorToken(SensorEvent<?> se) {
        makeEntitySensorToken(se.source, se.sensor)
    }

    protected final ExecutionManager em;
    
    protected ConcurrentMap<String, Subscription> allSubscriptions = new ConcurrentHashMap<String, Subscription>();
    protected ConcurrentMap<Object, Set<Subscription>> subscriptionsBySubscriber = new ConcurrentHashMap<Object, Set<Subscription>>()
    protected ConcurrentMap<Object, Set<Subscription>> subscriptionsByToken = new ConcurrentHashMap<Object, Set<Subscription>>()
	
    //REVIEW 1459 - add'l maps for efficient lookup
    
	public LocalSubscriptionManager(ExecutionManager em) { this.em = em }
    
    //REVIEW 1459 - removed "entities" predicate because at that generality it is hard to implement wide area; not used anyway
  
      
//subscriberExecutionManagerTag
    
    public <T> SubscriptionHandle subscribe(Entity producer, Sensor<T> sensor, EventListener<T> listener) {
        subscribe([:], producer, sensor, listener)
    }
    /** @see SubscriptionManager#subscribe(Map, Entity, Sensor, EventListener)
     * <p>
     * This implementation also adds flags:
     * <li>subscriberExecutionManagerTag - a tag to pass to execution manager (without setting any execution semantics / TaskPreprocessor);
     *      if not supplied and there is a subscriber, this will be inferred from the subscriber and set up with SingleThreadedExecution
     *      (supply this flag with value null to prevent any task preprocessor from being set)
     * <li>eventFilter - a Predicate<SensorEvent> instance to filter what events are delivered
     */
    public <T> SubscriptionHandle subscribe(Map<String, Object> flags, Entity producer, Sensor<T> sensor, EventListener<T> listener) {
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
            synchronized (this) {
                ((BasicExecutionManager)em).setTaskPreprocessorForTag(s.subscriberExecutionManagerTag, SingleThreadedExecution.class);
            }
        }
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
            synchronized (this) {
                //if subscriber has gone away forget about his task; but check in synch block to ensure setTaskPreprocessor call above will win in any race
                if (!subscriptionsBySubscriber.get(s.subscriber))
                    ((BasicExecutionManager)em).clearTaskPreprocessorForTag(s.subscriberExecutionManagerTag);
            }
        }

        ((BasicExecutionManager)em).setTaskPreprocessorForTag(s.subscriberExecutionManagerTag, SingleThreadedExecution.class);
        return b1
    }

    public Set<SubscriptionHandle> getSubscriptionsForSubscriber(Object subscriber) {
        return subscriptionsBySubscriber.get(subscriber) ?: Collections.emptySet()
    }
    public Set<SubscriptionHandle> getSubscriptionsForEntitySensor(Entity source, Sensor sensor) {
        return subscriptionsByToken.get( makeEntitySensorToken(source, sensor) ) ?: Collections.emptySet()
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
                em.submit(tags: s.subscriber, { s.listener.onEvent(event) })
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
        return "Subscription[$id;$subscriber@"+makeEntitySensorToken(producer,sensor)+"]"
    }
}
