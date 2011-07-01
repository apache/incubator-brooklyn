package brooklyn.policy

import java.util.Collections.UnmodifiableList

import brooklyn.entity.Entity
import brooklyn.event.EventListener
import brooklyn.event.Sensor
import brooklyn.event.SensorEvent
import brooklyn.event.basic.BasicSensor
import brooklyn.management.SubscriptionHandle
import brooklyn.policy.basic.AbstractPolicy

class BufferingEnricher<T> extends AbstractPolicy implements EventListener<T> {
    private Sensor<List<T>> result
    private boolean alwaysRetainBuffer
    
    protected LinkedList<T> buffer = new LinkedList<T>()
    
    public BufferingEnricher(Entity producer, Sensor<T> source, boolean alwaysRetainBuffer) {
        this.alwaysRetainBuffer = alwaysRetainBuffer
        this.result = new BasicSensor(List.class, "Buffer", "Buffer for ${source.getDescription()}")
        super.setEntity(producer)
        super.subscribe(producer, source, this)
    }
    
    public void onEvent(SensorEvent<T> e) {
        if (!discardBuffer()) {
            buffer.addFirst(e.getValue())
            manageBuffer()
            subscription.publish(result.newEvent(this, getBuffer()))
        }
    }
    
    public <T> void unsubscribe(long subscriptionId) {
        subscription.unsubscribe subscriptionId
        if (discardBuffer()) {buffer = new LinkedList<T>()}
    }
    
    protected void manageBuffer() {} //for overriding by subclasses
    
    private List<T> getBuffer() {
        if (discardBuffer()) {
            throw new NullPointerException("No buffer available as there are no subscribers and retention policy is set to NEVER")
        }
        new UnmodifiableList<T>(buffer)
    }
    
    public void flushBuffer() {
        buffer = new LinkedList<T>()
    }
    
    private boolean discardBuffer() {alwaysRetainBuffer && subscription.getSubscriptions().size() == 0}
    
    public static class RangeCachingEnricher<T> extends BufferingEnricher<T> {
        static final int DEFAULT_BUFFER_SIZE = 1
        
        private int cacheSize = DEFAULT_BUFFER_SIZE
        private Map<SubscriptionHandle, Integer> cacheSizes = new HashMap<Long, Integer>()
        
        public RangeCachingEnricher(int cacheSize) {this.cacheSize = cacheSize}
        
        public void manageBuffer() {
            removeStaleData()
        }
        
        public <T> SubscriptionHandle subscribe(Entity producer, Sensor<T> sensor, EventListener<T> listener) {
            this.subscribe(producer, sensor, listener, DEFAULT_BUFFER_SIZE)
        }
        
        public <T> SubscriptionHandle subscribe(Entity producer, Sensor<T> sensor, EventListener<T> listener, int bufferSize) {
            SubscriptionHandle subscriptionId = super.subscribe producer, sensor, listener
            cacheSizes.put(subscriptionId, bufferSize)
            updateCacheSize()
            subscriptionId
        }
        
        public <T> void unsubscribe(long subscriptionId) {
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
                buffer.removeLast()
            }
        }
    }
    
    public static class TimeCachingEnricher<T> extends BufferingEnricher<T> {
        static final int DEFAULT_BUFFER_TIME = 1000
        
        private LinkedList<Long> timestamps = new LinkedList<Long>()
        private long timeToCache = DEFAULT_BUFFER_TIME
        private Map<SubscriptionHandle, Integer> cacheTimes = new HashMap<Long, Integer>()
        
        public TimeCachingEnricher(long timeToCache) {this.timeToCache = timeToCache}
        
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
}
