package brooklyn.management.internal;

import static brooklyn.util.JavaGroovyEquivalents.elvis;
import static brooklyn.util.JavaGroovyEquivalents.groovyTruth;
import static brooklyn.util.JavaGroovyEquivalents.join;
import static brooklyn.util.JavaGroovyEquivalents.mapOf;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.Group;
import brooklyn.event.Sensor;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.management.ExecutionManager;
import brooklyn.management.SubscriptionHandle;
import brooklyn.management.SubscriptionManager;
import brooklyn.util.internal.LanguageUtils;
import brooklyn.util.task.BasicExecutionManager;
import brooklyn.util.task.SingleThreadedScheduler;

import com.google.common.base.Objects;
import com.google.common.base.Predicate;

/**
 * A {@link SubscriptionManager} that stores subscription details locally.
 */
public class LocalSubscriptionManager implements SubscriptionManager {
    
    // TODO Perhaps could use guava's SynchronizedSetMultimap? But need to check its synchronization guarantees.
    //      That would replace the utils used for subscriptionsBySubscriber etc.
    
    private static final Logger LOG = LoggerFactory.getLogger(SubscriptionManager.class);

    public static class EntitySensorToken {
        Entity e;
        Sensor<?> s;
        public EntitySensorToken(Entity e, Sensor<?> s) {
            this.e = e;
            this.s = s;
        }
        @Override
        public int hashCode() {
            return Objects.hashCode(e, s);
        }
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof EntitySensorToken)) return false;
            if (!Objects.equal(e, ((EntitySensorToken)obj).e)) return false;
            if (!Objects.equal(s, ((EntitySensorToken)obj).s)) return false;
            return true;
        }
        @Override
        public String toString() {
            return (e != null ? e.getId() :  "*")+":"+(s != null ? s.getName() : "*");
        }
    }
    static Object makeEntitySensorToken(Entity e, Sensor<?> s) {
        return new EntitySensorToken(e, s);
    }
    static Object makeEntitySensorToken(SensorEvent<?> se) {
        return makeEntitySensorToken(se.getSource(), se.getSensor());
    }

    protected final ExecutionManager em;
    
    protected ConcurrentMap<String, Subscription> allSubscriptions = new ConcurrentHashMap<String, Subscription>();
    protected ConcurrentMap<Object, Set<Subscription>> subscriptionsBySubscriber = new ConcurrentHashMap<Object, Set<Subscription>>();
    protected ConcurrentMap<Object, Set<Subscription>> subscriptionsByToken = new ConcurrentHashMap<Object, Set<Subscription>>();
    
    private final AtomicLong totalEventsPublishedCount = new AtomicLong();
    
    private final AtomicLong totalEventsDeliveredCount = new AtomicLong();
    
    public LocalSubscriptionManager(ExecutionManager m) {
        this.em = m;
    }

    public long getTotalEventsPublished() {
        return totalEventsPublishedCount.get();
    }
    
    public long getTotalEventsDelivered() {
        return totalEventsDeliveredCount.get();
    }
    
    public long getNumSubscriptions() {
        return allSubscriptions.size();
    }
    
    /** @see SubscriptionManager#subscribe(Map, Entity, Sensor, SensorEventListener) */
    public <T> SubscriptionHandle subscribe(Entity producer, Sensor<T> sensor, SensorEventListener<? super T> listener) {
        return subscribe(Collections.<String,Object>emptyMap(), producer, sensor, listener);
    }
 
    /**
     * This implementation handles the following flags, in addition to those described in the {@link SubscriptionManager}
     * interface:
     * <ul>
     * <li>subscriberExecutionManagerTag - a tag to pass to execution manager (without setting any execution semantics / TaskPreprocessor);
     *      if not supplied and there is a subscriber, this will be inferred from the subscriber and set up with SingleThreadedScheduler
     *      (supply this flag with value null to prevent any task preprocessor from being set)
     * <li>eventFilter - a Predicate&lt;SensorEvent&gt; instance to filter what events are delivered
     * </ul>
     * 
     * @see SubscriptionManager#subscribe(Map, Entity, Sensor, SensorEventListener)
     */
    public synchronized <T> SubscriptionHandle subscribe(Map<String, Object> flags, Entity producer, Sensor<T> sensor, SensorEventListener<? super T> listener) {
        Subscription s = new Subscription(producer, sensor, listener);
        s.subscriber = flags.containsKey("subscriber") ? flags.remove("subscriber") : listener;
        if (flags.containsKey("subscriberExecutionManagerTag")) {
            s.subscriberExecutionManagerTag = flags.remove("subscriberExecutionManagerTag");
            s.subscriberExecutionManagerTagSupplied = true;
        } else {
            s.subscriberExecutionManagerTag = 
                s.subscriber instanceof Entity ? "subscription-delivery-entity-"+((Entity)s.subscriber).getId()+"["+s.subscriber+"]" : 
                s.subscriber instanceof String ? "subscription-delivery-string["+s.subscriber+"]" : 
                s != null ? "subscription-delivery-object["+s.subscriber+"]" : null;
            s.subscriberExecutionManagerTagSupplied = false;
        }
        s.eventFilter = (Predicate) flags.remove("eventFilter");
        s.flags = flags;
        
        if (LOG.isDebugEnabled()) LOG.debug("Creating subscription {} for {} on {} {} in {}", new Object[] {s, s.subscriber, producer, sensor, this});
        allSubscriptions.put(s.id, s);
        LanguageUtils.addToMapOfSets(subscriptionsByToken, makeEntitySensorToken(s.producer, s.sensor), s);
        if (s.subscriber!=null) {
            LanguageUtils.addToMapOfSets(subscriptionsBySubscriber, s.subscriber, s);
        }
        if (!s.subscriberExecutionManagerTagSupplied && s.subscriberExecutionManagerTag!=null) {
            ((BasicExecutionManager) em).setTaskSchedulerForTag(s.subscriberExecutionManagerTag, SingleThreadedScheduler.class);
        }
        return s;
    }
  
    /** @see SubscriptionManager#subscribeToChildren(Map, Entity, Sensor, SensorEventListener) */
    public <T> SubscriptionHandle subscribeToChildren(Entity parent, Sensor<T> sensor, SensorEventListener<? super T> listener) {
        return subscribeToChildren(Collections.<String,Object>emptyMap(), parent, sensor, listener);
    }

    /** @see SubscriptionManager#subscribe(Map, Entity, Sensor, SensorEventListener) */
    public synchronized <T> SubscriptionHandle subscribeToChildren(Map<String, Object> flags, final Entity parent, Sensor<T> sensor, SensorEventListener<? super T> listener) {
        Predicate<SensorEvent<T>> eventFilter = new Predicate<SensorEvent<T>>() {
            public boolean apply(SensorEvent<T> input) {
                return parent.getOwnedChildren().contains(input.getSource());
            }
        };
        flags.put("eventFilter", eventFilter);
        return subscribe(flags, null, sensor, listener);
    }

    /** @see SubscriptionManager#subscribeToChildren(Map, Entity, Sensor, SensorEventListener) */
    public <T> SubscriptionHandle subscribeToMembers(Group parent, Sensor<T> sensor, SensorEventListener<? super T> listener) {
        return subscribeToMembers(Collections.<String,Object>emptyMap(), parent, sensor, listener);
    }

    /** @see SubscriptionManager#subscribe(Map, Entity, Sensor, SensorEventListener) */
    public synchronized <T> SubscriptionHandle subscribeToMembers(Map<String, Object> flags, final Group parent, Sensor<T> sensor, SensorEventListener<? super T> listener) {
        Predicate<SensorEvent<T>> eventFilter = new Predicate<SensorEvent<T>>() {
            public boolean apply(SensorEvent<T> input) {
                return parent.getMembers().contains(input.getSource());
            }
        };
        flags.put("eventFilter", eventFilter);
        return subscribe(flags, null, sensor, listener);
    }
    /**
     * Unsubscribe the given subscription id.
     *
     * @see #subscribe(Map, Entity, Sensor, SensorEventListener)
     */
    public synchronized boolean unsubscribe(SubscriptionHandle sh) {
        if (!(sh instanceof Subscription)) throw new IllegalArgumentException("Only subscription handles of type Subscription supported: sh="+sh+"; type="+(sh != null ? sh.getClass().getCanonicalName() : null));
        Subscription s = (Subscription) sh;
        boolean b1 = allSubscriptions.remove(s.id) != null;
        boolean b2 = LanguageUtils.removeFromMapOfCollections(subscriptionsByToken, makeEntitySensorToken(s.producer, s.sensor), s);
        assert b1==b2;
        if (s.subscriber!=null) {
            boolean b3 = LanguageUtils.removeFromMapOfCollections(subscriptionsBySubscriber, s.subscriber, s);
            assert b3 == b2;
        }
        
        // TODO Requires code review: why did we previously do exactly same check twice in a row (with no synchronization in between)? 
        if ((subscriptionsBySubscriber.size() == 0 || !groovyTruth(subscriptionsBySubscriber.get(s.subscriber))) && !s.subscriberExecutionManagerTagSupplied && s.subscriberExecutionManagerTag!=null) {
            //if subscriber has gone away forget about his task; but check in synch block to ensure setTaskPreprocessor call above will win in any race
            if ((subscriptionsBySubscriber.size() == 0 || !groovyTruth(subscriptionsBySubscriber.get(s.subscriber))))
                ((BasicExecutionManager)em).clearTaskPreprocessorForTag(s.subscriberExecutionManagerTag);
        }

		//FIXME ALEX - this seems wrong
        ((BasicExecutionManager) em).setTaskSchedulerForTag(s.subscriberExecutionManagerTag, SingleThreadedScheduler.class);
        return b1;
    }

    @SuppressWarnings("unchecked")
    public Set<SubscriptionHandle> getSubscriptionsForSubscriber(Object subscriber) {
        return (Set<SubscriptionHandle>) ((Set<?>) elvis(subscriptionsBySubscriber.get(subscriber), Collections.emptySet()));
    }

    public synchronized Set<SubscriptionHandle> getSubscriptionsForEntitySensor(Entity source, Sensor<?> sensor) {
        Set<SubscriptionHandle> subscriptions = new LinkedHashSet<SubscriptionHandle>();
        subscriptions.addAll(elvis(subscriptionsByToken.get(makeEntitySensorToken(source, sensor)), Collections.emptySet()));
        subscriptions.addAll(elvis(subscriptionsByToken.get(makeEntitySensorToken(null, sensor)), Collections.emptySet()));
        subscriptions.addAll(elvis(subscriptionsByToken.get(makeEntitySensorToken(source, null)), Collections.emptySet()));
        subscriptions.addAll(elvis(subscriptionsByToken.get(makeEntitySensorToken(null, null)), Collections.emptySet()));
        return subscriptions;
    }

    public <T> void publish(final SensorEvent<T> event) {
        // REVIEW 1459 - execution
        
        // delivery in parallel/background, using execution manager
        
        // subscriptions, should define SingleThreadedScheduler for any subscriber ID tag
        // in order to ensure callbacks are invoked in the order they are submitted
        // (recommend exactly one per subscription to prevent deadlock)
        // this is done with:
        // em.setTaskSchedulerForTag(subscriberId, SingleThreadedScheduler.class);
        
        //note, generating the notifications must be done in the calling thread to preserve order
        //e.g. emit(A); emit(B); should cause onEvent(A); onEvent(B) in that order
        if (LOG.isTraceEnabled()) LOG.trace("{} got a {} event", this, event);
        totalEventsPublishedCount.incrementAndGet();
        
        Set<Subscription> subs = (Set<Subscription>) ((Set<?>) getSubscriptionsForEntitySensor(event.getSource(), event.getSensor()));
        if (groovyTruth(subs)) {
            if (LOG.isTraceEnabled()) LOG.trace("sending {}, {} to {}", new Object[] {event.getSensor().getName(), event, join(subs, ",")});
            for (Subscription s : subs) {
                if (s.eventFilter!=null && !s.eventFilter.apply(event))
                    continue;
                final Subscription sAtClosureCreation = s;
                em.submit(mapOf("tag", s.subscriberExecutionManagerTag), new Runnable() {
                    public void run() {
                        sAtClosureCreation.listener.onEvent(event);
                    }});
                totalEventsDeliveredCount.incrementAndGet();
            }
        }
    }
}
