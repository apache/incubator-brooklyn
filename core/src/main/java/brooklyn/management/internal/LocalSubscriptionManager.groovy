package brooklyn.management.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import brooklyn.entity.Entity;
import brooklyn.event.EventListener;
import brooklyn.event.Sensor;
import brooklyn.event.basic.EventFilters;
import brooklyn.event.basic.SensorEvent;
import brooklyn.management.SubscriptionManager;

import com.google.common.base.Objects;
import com.google.common.base.Predicate;

public class LocalSubscriptionManager implements SubscriptionManager {
    private static class Subscription {
        public String interestedId;
        public Predicate<Entity> entities;
        public Predicate<SensorEvent<?>> filter;
        public EventListener<?> listener;

        public Subscription(String interestedId, Predicate<Entity> entities, Predicate<SensorEvent<?>> filter, EventListener<?> listener) {
            this.interestedId = interestedId;
            this.entities = entities;
            this.filter = filter;
            this.listener = listener;
        }
        
        @Override
        public boolean equals(Object other) {
            return Objects.equal(this, other);
        }
 
        @Override
        public int hashCode() {
            return Objects.hashCode(interestedId, entities, filter, listener);
        }
    }
    
    AtomicLong subscriptionId = new AtomicLong(0L);
    ConcurrentMap<Long, Subscription> allSubscriptions = new ConcurrentHashMap<Long, Subscription>();
    ConcurrentMap<String, List<Subscription>> entitySubscriptionList = new ConcurrentHashMap<String, List<Subscription>>();
    Lock lock = new ReentrantLock();
    
    public void fire(SensorEvent<?> event) {
        lock.lock();
        try {
            allSubscriptions.each { key, Subscription s -> if (s.filter.apply(event) && (!s.entities || s.entities.apply(event))) s.listener.onEvent(event) }
        } finally {
            lock.unlock();
        }
    }

    public <T> long subscribe(String interestedId, String producerId, String sensorName, EventListener<T> listener) {
        Subscription sub = new Subscription(interestedId, EventFilters.entityId(producerId), EventFilters.sensorName(sensorName), listener);
        long id = subscriptionId.incrementAndGet();
        lock.lock();
        try {
            allSubscriptions.put(id, sub);
            entitySubscriptionList.putIfAbsent(interestedId, new CopyOnWriteArrayList<Subscription>());
            List<Subscription> entitySubscriptions = entitySubscriptionList.get(interestedId);
            entitySubscriptions.add(sub);
        } finally {
            lock.unlock();
        }
        return id;
    }

    public void unsubscribe(long subscriptionId) {
        lock.lock();
        try {
	        Subscription subscription = allSubscriptions.get(subscriptionId);
	        allSubscriptions.remove(subscriptionId);
            List<Subscription> entitySubscriptions = entitySubscriptionList.get(subscription.interestedId);
            entitySubscriptions.remove(subscription);
        } finally {
            lock.unlock();
        }
    }
}
