package brooklyn.policy.wip

import java.util.HashMap
import java.util.Map

import brooklyn.entity.Entity
import brooklyn.event.EventListener
import brooklyn.event.Sensor
import brooklyn.management.SubscriptionHandle

public class RangeBufferEnricher<T> extends BufferingEnricher<T> {
    static final int DEFAULT_BUFFER_SIZE = 1

    private int cacheSize = DEFAULT_BUFFER_SIZE
    private Map<SubscriptionHandle, Integer> cacheSizes = new HashMap<Long, Integer>()

    public RangeBufferEnricher(Entity owner, Entity producer, Sensor<T> source, int cacheSize) {
        super(owner, producer, source)
        this.cacheSize = cacheSize
    }

    public void manageBuffer() {
        removeStaleData()
    }

    public <T> SubscriptionHandle subscribe(Entity producer, Sensor<T> sensor, EventListener<T> listener) {
        this.subscribe(producer, sensor, listener, DEFAULT_BUFFER_SIZE)
    }

    protected <T> SubscriptionHandle subscribe(Entity producer, Sensor<T> sensor, EventListener<T> listener, int bufferSize) {
        SubscriptionHandle subscriptionId = super.subscribe producer, sensor, listener
        cacheSizes.put(subscriptionId, bufferSize)
        updateCacheSize()
        subscriptionId
    }

    protected <T> void unsubscribe(long subscriptionId) {
        super.unsubscribe subscriptionId
        cacheSizes.remove(subscriptionId)
        updateCacheSize()
    }

    private void updateCacheSize() {
        cacheSize = cacheSizes.values().max()
        removeStaleData()
    }

    private void removeStaleData() {
        while (cacheSize < buffer.size()) {
            this.@buffer.removeLast()
        }
    }
}