/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package brooklyn.management.internal;

import static brooklyn.util.JavaGroovyEquivalents.elvis;
import static brooklyn.util.JavaGroovyEquivalents.groovyTruth;
import static brooklyn.util.JavaGroovyEquivalents.join;
import static brooklyn.util.JavaGroovyEquivalents.mapOf;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.event.Sensor;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.management.ExecutionManager;
import brooklyn.management.SubscriptionHandle;
import brooklyn.management.SubscriptionManager;
import brooklyn.util.task.BasicExecutionManager;
import brooklyn.util.task.SingleThreadedScheduler;
import brooklyn.util.text.Identifiers;

import com.google.common.base.Predicate;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimaps;

/**
 * A {@link SubscriptionManager} that stores subscription details locally.
 */
public class LocalSubscriptionManager extends AbstractSubscriptionManager {
    
    private static final Logger LOG = LoggerFactory.getLogger(LocalSubscriptionManager.class);

    protected final ExecutionManager em;
    
    private final String tostring = "SubscriptionContext("+Identifiers.getBase64IdFromValue(System.identityHashCode(this), 5)+")";

    private final AtomicLong totalEventsPublishedCount = new AtomicLong();
    private final AtomicLong totalEventsDeliveredCount = new AtomicLong();
    
    @SuppressWarnings("rawtypes")
    protected final ConcurrentMap<String, Subscription> allSubscriptions = new ConcurrentHashMap<String, Subscription>();
    @SuppressWarnings("rawtypes")
    protected final ConcurrentMap<Object, Set<Subscription>> subscriptionsBySubscriber = new ConcurrentHashMap<Object, Set<Subscription>>();
    @SuppressWarnings("rawtypes")
    protected final ConcurrentMap<Object, Set<Subscription>> subscriptionsByToken = new ConcurrentHashMap<Object, Set<Subscription>>();
    
    public LocalSubscriptionManager(ExecutionManager m) {
        this.em = m;
    }
        
    public long getNumSubscriptions() {
        return allSubscriptions.size();
    }

    public long getTotalEventsPublished() {
        return totalEventsPublishedCount.get();
    }
    
    public long getTotalEventsDelivered() {
        return totalEventsDeliveredCount.get();
    }
    
    @SuppressWarnings("unchecked")
    protected synchronized <T> SubscriptionHandle subscribe(Map<String, Object> flags, Subscription<T> s) {
        Entity producer = s.producer;
        Sensor<T> sensor= s.sensor;
        s.subscriber = getSubscriber(flags, s);
        if (flags.containsKey("subscriberExecutionManagerTag")) {
            s.subscriberExecutionManagerTag = flags.remove("subscriberExecutionManagerTag");
            s.subscriberExecutionManagerTagSupplied = true;
        } else {
            s.subscriberExecutionManagerTag = 
                s.subscriber instanceof Entity ? "subscription-delivery-entity-"+((Entity)s.subscriber).getId()+"["+s.subscriber+"]" : 
                s.subscriber instanceof String ? "subscription-delivery-string["+s.subscriber+"]" : 
                "subscription-delivery-object["+s.subscriber+"]";
            s.subscriberExecutionManagerTagSupplied = false;
        }
        s.eventFilter = (Predicate<SensorEvent<T>>) flags.remove("eventFilter");
        s.flags = flags;
        
        if (LOG.isDebugEnabled()) LOG.debug("Creating subscription {} for {} on {} {} in {}", new Object[] {s.id, s.subscriber, producer, sensor, this});
        allSubscriptions.put(s.id, s);
        addToMapOfSets(subscriptionsByToken, makeEntitySensorToken(s.producer, s.sensor), s);
        if (s.subscriber!=null) {
            addToMapOfSets(subscriptionsBySubscriber, s.subscriber, s);
        }
        if (!s.subscriberExecutionManagerTagSupplied && s.subscriberExecutionManagerTag!=null) {
            ((BasicExecutionManager) em).setTaskSchedulerForTag(s.subscriberExecutionManagerTag, SingleThreadedScheduler.class);
        }
        return s;
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

    /**
     * Unsubscribe the given subscription id.
     *
     * @see #subscribe(Map, Entity, Sensor, SensorEventListener)
     */
    @SuppressWarnings("rawtypes")
    public synchronized boolean unsubscribe(SubscriptionHandle sh) {
        if (!(sh instanceof Subscription)) throw new IllegalArgumentException("Only subscription handles of type Subscription supported: sh="+sh+"; type="+(sh != null ? sh.getClass().getCanonicalName() : null));
        Subscription s = (Subscription) sh;
        boolean result = allSubscriptions.remove(s.id) != null;
        boolean b2 = removeFromMapOfCollections(subscriptionsByToken, makeEntitySensorToken(s.producer, s.sensor), s);
        assert result==b2;
        if (s.subscriber!=null) {
            boolean b3 = removeFromMapOfCollections(subscriptionsBySubscriber, s.subscriber, s);
            assert b3 == b2;
        }

        // FIXME ALEX - this seems wrong
        ((BasicExecutionManager) em).setTaskSchedulerForTag(s.subscriberExecutionManagerTag, SingleThreadedScheduler.class);
        return result;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
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
        if (LOG.isTraceEnabled()) LOG.trace("{} got event {}", this, event);
        totalEventsPublishedCount.incrementAndGet();
        
        Set<Subscription> subs = (Set<Subscription>) ((Set<?>) getSubscriptionsForEntitySensor(event.getSource(), event.getSensor()));
        if (groovyTruth(subs)) {
            if (LOG.isTraceEnabled()) LOG.trace("sending {}, {} to {}", new Object[] {event.getSensor().getName(), event, join(subs, ",")});
            for (Subscription s : subs) {
                if (s.eventFilter!=null && !s.eventFilter.apply(event))
                    continue;
                final Subscription sAtClosureCreation = s;
                em.submit(mapOf("tag", s.subscriberExecutionManagerTag), new Runnable() {
                    @Override
                    public String toString() {
                        return "LSM.publish("+event+")";
                    }
                    public void run() {
                        try {
                            sAtClosureCreation.listener.onEvent(event);
                        } catch (Throwable t) {
                            LOG.warn("Error in "+this+": "+t, t);
                        }
                    }});
                totalEventsDeliveredCount.incrementAndGet();
            }
        }
    }
    
    @Override
    public String toString() {
        return tostring;
    }
    
    /**
     * Copied from LanguageUtils.groovy, to remove dependency.
     * 
     * Adds the given value to a collection in the map under the key.
     * 
     * A collection (as {@link LinkedHashMap}) will be created if necessary,
     * synchronized on map for map access/change and set for addition there
     *
     * @return the updated set (instance, not copy)
     * 
     * @deprecated since 0.5; use {@link HashMultimap}, and {@link Multimaps#synchronizedSetMultimap(com.google.common.collect.SetMultimap)}
     */
    @Deprecated
    private static <K,V> Set<V> addToMapOfSets(Map<K,Set<V>> map, K key, V valueInCollection) {
        Set<V> coll;
        synchronized (map) {
            coll = map.get(key);
            if (coll==null) {
                coll = new LinkedHashSet<V>();
                map.put(key, coll);
            }
            if (coll.isEmpty()) {
                synchronized (coll) {
                    coll.add(valueInCollection);
                }
                //if collection was empty then add to the collection while holding the map lock, to prevent removal
                return coll;
            }
        }
        synchronized (coll) {
            if (!coll.isEmpty()) {
                coll.add(valueInCollection);
                return coll;
            }
        }
        //if was empty, recurse, because someone else might be removing the collection
        return addToMapOfSets(map, key, valueInCollection);
    }

    /**
     * Copied from LanguageUtils.groovy, to remove dependency.
     * 
     * Removes the given value from a collection in the map under the key.
     *
     * @return the updated set (instance, not copy)
     * 
     * @deprecated since 0.5; use {@link ArrayListMultimap} or {@link HashMultimap}, and {@link Multimaps#synchronizedListMultimap(com.google.common.collect.ListMultimap)} etc
     */
    @Deprecated
    private static <K,V> boolean removeFromMapOfCollections(Map<K,? extends Collection<V>> map, K key, V valueInCollection) {
        Collection<V> coll;
        synchronized (map) {
            coll = map.get(key);
            if (coll==null) return false;
        }
        boolean result;
        synchronized (coll) {
            result = coll.remove(valueInCollection);
        }
        if (coll.isEmpty()) {
            synchronized (map) {
                synchronized (coll) {
                    if (coll.isEmpty()) {
                        //only remove from the map if no one is adding to the collection or to the map, and the collection is still in the map
                        if (map.get(key)==coll) {
                            map.remove(key);
                        }
                    }
                }
            }
        }
        return result;
    }
}
