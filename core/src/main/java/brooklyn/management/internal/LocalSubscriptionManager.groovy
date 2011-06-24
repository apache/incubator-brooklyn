package brooklyn.management.internal;

import java.util.List
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock

import brooklyn.entity.Entity
import brooklyn.event.SensorEvent
import brooklyn.event.EventListener
import brooklyn.event.basic.EventFilters
import brooklyn.event.basic.BasicSensorEvent
import brooklyn.management.SubscriptionManager

import com.google.common.base.Objects
import com.google.common.base.Predicate

/**
 * A {@link SubscriptionManager} that stores subscription details locally.
 */
public class LocalSubscriptionManager implements SubscriptionManager {
    private static class Subscription {
        public Predicate<Entity> entities;
        public Predicate<BasicSensorEvent<?>> filter;
        public EventListener<?> listener;

        public Subscription(Predicate<Entity> entities, Predicate<BasicSensorEvent<?>> filter, EventListener<?> listener) {
            this.entities = entities;
            this.filter = filter;
            this.listener = listener;
        }
        
        @Override
        public boolean equals(Object other) {
            // FIXME proper equals
            return Objects.equal(this, other);
        }
 
        @Override
        public int hashCode() {
            return Objects.hashCode(entities, filter, listener);
        }
    }
    
    AtomicLong subscriptionId = new AtomicLong(0L);
    ConcurrentMap<Long, Subscription> allSubscriptions = new ConcurrentHashMap<Long, Subscription>();
    
    public <T> void publish(SensorEvent<T> event) {
        allSubscriptions.each { key, Subscription s -> if (s.filter.apply(event) && (!s.entities || s.entities.apply(event))) s.listener.onEvent(event) }
    }

    public <T> long subscribe(String producerId, String sensorName, EventListener<T> listener) {
        Subscription sub = new Subscription(EventFilters.entityId(producerId), EventFilters.sensorName(sensorName), listener);
        long id = subscriptionId.incrementAndGet();
        allSubscriptions.put(id, sub);
        return id;
    }

    public void unsubscribe(long subscriptionId) {
        allSubscriptions.remove(subscriptionId);
    }
}
