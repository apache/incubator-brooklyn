package brooklyn.policy

import java.util.LinkedList;
import java.util.Map;

import brooklyn.entity.Entity;
import brooklyn.event.EventListener;
import brooklyn.event.Sensor;
import brooklyn.management.SubscriptionHandle;

public class TimeBufferEnricher<T> extends BufferingEnricher<T> {
    static final int DEFAULT_BUFFER_TIME = 1000

    private LinkedList<Long> timestamps = new LinkedList<Long>()
    private long timeToCache = DEFAULT_BUFFER_TIME
    private Map<SubscriptionHandle, Integer> cacheTimes = new HashMap<Long, Integer>()

    public TimeBufferEnricher(Entity owner, Entity producer, Sensor<T> source, long timeToCache) {
        super(owner, producer, source)
        this.timeToCache = timeToCache
    }

    protected void manageBuffer() {
        Long now = System.currentTimeMillis()
        timestamps.addFirst(now)
        removeStaleData(now - timeToCache)

    }

    public <T> SubscriptionHandle subscribe(Entity producer, Sensor<T> sensor, EventListener<T> listener) {
        this.subscribe(producer, sensor, listener, DEFAULT_BUFFER_TIME)
    }

    public <T> SubscriptionHandle subscribe(Entity producer, Sensor<T> sensor, EventListener<T> listener, long bufferTime) {
        long subscriptionId = super.subscribe producer, sensor, listener
        cacheTimes.put(subscriptionId, bufferTime)
        updateCacheSize()
        subscriptionId
    }

    public <T> void unsubscribe(long subscriptionId) {
        super.unsubscribe subscriptionId
        cacheTimes.remove(subscriptionId)
        updateCacheSize()
    }

    private void updateCacheSize() {
        timeToCache = cacheTimes.values().max()
        removeStaleData()
    }

    private void removeStaleData(long time) {
        while (time < timestamps.getLast()) {
            timestamps.removeLast()
            buffer.removeLast()
        }
    }
}