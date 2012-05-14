package brooklyn.policy.resizing

import groovy.lang.Closure

import java.util.Map
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Entity
import brooklyn.entity.basic.EntityLocal
import brooklyn.entity.trait.Resizable
import brooklyn.event.SensorEvent
import brooklyn.event.SensorEventListener
import brooklyn.event.basic.BasicNotificationSensor
import brooklyn.policy.basic.AbstractPolicy
import brooklyn.util.TimeWindowedList
import brooklyn.util.TimestampedValue
import brooklyn.util.flags.SetFromFlag

import com.google.common.base.Preconditions


/**
 * Policy that is attached to a <code>Resizable</code> entity and dynamically adjusts its size in response to
 * emitted <code>POOL_COLD</code> and <code>POOL_HOT</code> events. (This policy does not itself determine whether
 * the pool is hot or cold, but instead relies on these events being emitted by the monitored entity itself, or
 * by another policy that is attached to it; see, for example, {@link LoadBalancingPolicy}.)
 */
public class ResizingPolicy extends AbstractPolicy {
    
    private static final Logger LOG = LoggerFactory.getLogger(ResizingPolicy.class)
    
    // Pool workrate notifications.
    public static BasicNotificationSensor<Map> POOL_HOT = new BasicNotificationSensor<Map>(
        Map.class, "resizablepool.hot", "Pool is over-utilized; it has insufficient resource for current workload")
    public static BasicNotificationSensor<Map> POOL_COLD = new BasicNotificationSensor<Map>(
        Map.class, "resizablepool.cold", "Pool is under-utilized; it has too much resource for current workload")
    public static BasicNotificationSensor<Map> POOL_OK = new BasicNotificationSensor<Map>(
        Map.class, "resizablepool.cold", "Pool utilization is ok; the available resources are fine for the current workload")

    public static final String POOL_CURRENT_SIZE_KEY = "pool.current.size"
    public static final String POOL_HIGH_THRESHOLD_KEY = "pool.high.threshold"
    public static final String POOL_LOW_THRESHOLD_KEY = "pool.low.threshold"
    public static final String POOL_CURRENT_WORKRATE_KEY = "pool.current.workrate"
    
    @SetFromFlag(defaultVal="100")
    private long minPeriodBetweenExecs
    
    @SetFromFlag
    private long resizeUpStabilizationDelay
    
    @SetFromFlag
    private long resizeDownStabilizationDelay
    
    @SetFromFlag
    private int minPoolSize
    
    @SetFromFlag(defaultVal="2147483647") // defaultVal=Integer.MAX_VALUE
    private int maxPoolSize
    
    @SetFromFlag
    private final Closure resizeOperator
    
    @SetFromFlag
    private final Closure currentSizeOperator
    
    @SetFromFlag
    private final BasicNotificationSensor poolHotSensor
    
    @SetFromFlag
    private final BasicNotificationSensor poolColdSensor
    
    @SetFromFlag
    private final BasicNotificationSensor poolOkSensor
    
    private Entity poolEntity
    
    private volatile ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor()
    private final AtomicBoolean executorQueued = new AtomicBoolean(false)
    private volatile long executorTime = 0
    
    private final TimeWindowedList recentDesiredResizes
    
    private final Closure defaultResizeOperator = { Entity e, int desiredSize ->
        ((Entity)e).resize(desiredSize)
    }
    
    private final Closure defaultCurrentSizeOperator = { Entity e ->
        return ((Entity)e).getCurrentSize()
    }
    
    private final SensorEventListener<?> eventHandler = new SensorEventListener<Object>() {
        public void onEvent(SensorEvent<Object> event) {
            Map<String, ?> properties = (Map<String, ?>) event.getValue()
            switch (event.getSensor()) {
                case poolColdSensor: onPoolCold(properties); break
                case poolHotSensor: onPoolHot(properties); break
                case poolOkSensor: onPoolOk(properties); break
            }
        }
    }
    
    public ResizingPolicy(Map props = [:]) {
        super(props)
        resizeOperator = resizeOperator ?: defaultResizeOperator
        currentSizeOperator = currentSizeOperator ?: defaultCurrentSizeOperator
        poolHotSensor = poolHotSensor ?: POOL_HOT
        poolColdSensor = poolColdSensor ?: POOL_COLD
        poolOkSensor = poolOkSensor ?: POOL_OK
        
        long maxResizeStabilizationDelay = Math.max(resizeUpStabilizationDelay, resizeDownStabilizationDelay)
        recentDesiredResizes = new TimeWindowedList<Number>([timePeriod:maxResizeStabilizationDelay, minExpiredVals:1])
    }
    
    @Override
    public void suspend() {
        super.suspend();
        // TODO unsubscribe from everything? And resubscribe on resume?
        if (executor != null) executor.shutdownNow()
    }
    
    @Override
    public void resume() {
        super.resume();
        executor = Executors.newSingleThreadScheduledExecutor()
    }
    
    @Override
    public void setEntity(EntityLocal entity) {
        if (resizeOperator == defaultResizeOperator) {
            Preconditions.checkArgument(entity instanceof Resizable, "Provided entity must be an instance of Resizable, because no custom-resizer operator supplied")
        }
        super.setEntity(entity)
        this.poolEntity = entity
        
        subscribe(poolEntity, poolColdSensor, eventHandler)
        subscribe(poolEntity, poolHotSensor, eventHandler)
        subscribe(poolEntity, poolOkSensor, eventHandler)
    }
    
    private void onPoolCold(Map<String, ?> properties) {
        if (LOG.isTraceEnabled()) LOG.trace("{} recording pool-cold for {}: {}", this, poolEntity, properties)
        
        int poolCurrentSize = properties.get(POOL_CURRENT_SIZE_KEY)
        double poolCurrentWorkrate = properties.get(POOL_CURRENT_WORKRATE_KEY)
        double poolLowThreshold = properties.get(POOL_LOW_THRESHOLD_KEY)
        
        // Shrink the pool to force its low threshold to fall below the current workrate.
        // NOTE: assumes the pool is homogeneous for now.
        int desiredPoolSize = Math.ceil(poolCurrentWorkrate / (poolLowThreshold/poolCurrentSize)).intValue()
        desiredPoolSize = toBoundedDesiredPoolSize(desiredPoolSize)
        if (desiredPoolSize < poolCurrentSize) {
            if (LOG.isTraceEnabled()) LOG.trace("{} resizing cold pool {} from {} to {}", this, poolEntity, poolCurrentSize, desiredPoolSize)
            scheduleResize(desiredPoolSize)
        } else {
            if (LOG.isTraceEnabled()) LOG.trace("{} not resizing cold pool {} from {} to {}", this, poolEntity, poolCurrentSize, desiredPoolSize)
            abortResize(poolCurrentSize)
        }

    }
    
    private void onPoolHot(Map<String, ?> properties) {
        if (LOG.isTraceEnabled()) LOG.trace("{} recording pool-hot for {}: {}", this, poolEntity, properties)
        
        int poolCurrentSize = properties.get(POOL_CURRENT_SIZE_KEY)
        double poolCurrentWorkrate = properties.get(POOL_CURRENT_WORKRATE_KEY)
        double poolHighThreshold = properties.get(POOL_HIGH_THRESHOLD_KEY)
        
        // Grow the pool to force its high threshold to rise above the current workrate.
        // FIXME: assumes the pool is homogeneous for now.
        int desiredPoolSize = Math.ceil(poolCurrentWorkrate / (poolHighThreshold/poolCurrentSize)).intValue()
        desiredPoolSize = toBoundedDesiredPoolSize(desiredPoolSize)
        if (desiredPoolSize > poolCurrentSize) {
            if (LOG.isTraceEnabled()) LOG.trace("{} resizing hot pool {} from {} to {}", this, poolEntity, poolCurrentSize, desiredPoolSize)
            scheduleResize(desiredPoolSize)
        } else {
            if (LOG.isTraceEnabled()) LOG.trace("{} not resizing hot pool {} from {} to {}", this, poolEntity, poolCurrentSize, desiredPoolSize)
            abortResize(poolCurrentSize)
        }
    }
    
    private void onPoolOk(Map<String, ?> properties) {
        if (LOG.isTraceEnabled()) LOG.trace("{} recording pool-ok for {}: {}", this, poolEntity, properties)
        
        int poolCurrentSize = properties.get(POOL_CURRENT_SIZE_KEY)
        
        if (LOG.isTraceEnabled()) LOG.trace("{} not resizing ok pool {} from {}", this, poolEntity, poolCurrentSize)
        abortResize(poolCurrentSize)
    }
    
    private int toBoundedDesiredPoolSize(int size) {
        int result = Math.max(minPoolSize, size)
        result = Math.min(maxPoolSize, result)
        return result
    }

    /**
     * Schedules a resize, if there is not already a resize operation queued up. When that resize
     * executes, it will resize to whatever the latest value is to be (rather than what it was told
     * to do at the point the job was queued).
     */
    private void scheduleResize(final int newSize) {
        recentDesiredResizes.add(newSize)
        
        scheduleResize()
    }

    private void abortResize(final int currentSize) {
        recentDesiredResizes.add(currentSize)
    }
    
    private void scheduleResize() {
        // TODO perhaps make concurrent calls, rather than waiting for first resize to entirely 
        // finish? On ec2 for example, this can cause us to grow very slowly if first request is for
        // just one new VM to be provisioned.
        
        if (isRunning() && executorQueued.compareAndSet(false, true)) {
            long now = System.currentTimeMillis()
            long delay = Math.max(0, (executorTime + minPeriodBetweenExecs) - now)
            if (LOG.isTraceEnabled()) LOG.trace("{} scheduling resize in {}ms", this, delay)
            
            executor.schedule(
                {
                    try {
                        executorTime = System.currentTimeMillis()
                        executorQueued.set(false)

                        long currentPoolSize = currentSizeOperator.call(poolEntity)
                        int desiredPoolSize = calculateDesiredPoolSize(currentPoolSize)
                        boolean stable = isDesiredPoolSizeStable()
                        if (!stable) {
                            // the desired size fluctuations are not stable; ensure we check again later (due to time-window)
                            // even if no additional events have been received
                            if (LOG.isTraceEnabled()) LOG.trace("{} re-scheduling resize check, as desired size not stable; continuing with resize...", 
                                    this, poolEntity, currentPoolSize, desiredPoolSize)
                            scheduleResize()
                        }
                        if (currentPoolSize == desiredPoolSize) {
                            if (LOG.isTraceEnabled()) LOG.trace("{} not resizing pool {} from {} to {}", this, poolEntity, currentPoolSize, desiredPoolSize)
                            return
                        }
                        
                        resizeOperator.call(poolEntity, desiredPoolSize)
                        
                        if (LOG.isDebugEnabled()) LOG.debug("{} requested resize to {}; current {}, min {}, max {}", this, desiredPoolSize,
                                currentPoolSize, minPoolSize, maxPoolSize)
                        
                    } catch (InterruptedException e) {
                        if (LOG.isDebugEnabled()) LOG.debug("Interrupted while attempting resize", e)
                        Thread.currentThread().interrupt() // gracefully stop
                    } catch (Exception e) {
                        if (isRunning()) {
                            LOG.error("Error resizing: "+e, e)
                        } else {
                            if (LOG.isDebugEnabled()) LOG.debug("Error resizing, but no longer running: "+e, e)
                        }
                    } catch (Throwable t) {
                        LOG.error("Error resizing: "+t, t)
                        throw t
                    }
                },
                delay,
                TimeUnit.MILLISECONDS)
        }
    }
    
    private boolean isDesiredPoolSizeStable() {
        long now = System.currentTimeMillis()
        List<TimestampedValue<?>> downWindowVals = recentDesiredResizes.getValuesInWindow(now, resizeDownStabilizationDelay)
        List<TimestampedValue<?>> upWindowVals = recentDesiredResizes.getValuesInWindow(now, resizeUpStabilizationDelay)
        return (minInWindow(downWindowVals, resizeDownStabilizationDelay) == maxInWindow(downWindowVals, resizeDownStabilizationDelay)) && 
                (minInWindow(upWindowVals, resizeUpStabilizationDelay) == maxInWindow(upWindowVals, resizeUpStabilizationDelay))
    }
    
    /**
     * Complicated logic for stabilization-delay...
     * Only grow if we have consistently been asked to grow for the resizeUpStabilizationDelay period;
     * Only shrink if we have consistently been asked to shrink for the resizeDownStabilizationDelay period.
     */
    private int calculateDesiredPoolSize(long currentPoolSize) {
        long now = System.currentTimeMillis()
        List<TimestampedValue<?>> downsizeWindowVals = recentDesiredResizes.getValuesInWindow(now, resizeDownStabilizationDelay)
        List<TimestampedValue<?>> upsizeWindowVals = recentDesiredResizes.getValuesInWindow(now, resizeUpStabilizationDelay)
        int minDesiredPoolSize = maxInWindow(downsizeWindowVals, resizeDownStabilizationDelay)
        int maxDesiredPoolSize = minInWindow(upsizeWindowVals, resizeUpStabilizationDelay)
        
        int desiredPoolSize
        if (currentPoolSize > minDesiredPoolSize) {
            // need to shrink
            desiredPoolSize = minDesiredPoolSize
        } else if (currentPoolSize < maxDesiredPoolSize) {
            // need to grow
            desiredPoolSize = maxDesiredPoolSize
        } else {
            desiredPoolSize = currentPoolSize
        }
        
        if (LOG.isTraceEnabled()) LOG.trace("{} calculated desired pool size: from {} to {}; minDesired {}, maxDesired {}; downsizeHistory {}; upsizeHistor {}", 
                this, currentPoolSize, desiredPoolSize, minDesiredPoolSize, maxDesiredPoolSize, downsizeWindowVals, upsizeWindowVals)
        
        return desiredPoolSize
    }

    /**
     * If the entire time-window is not covered by the given values, then returns Integer.MAX_VALUE.
     */
    private <T> T maxInWindow(List<TimestampedValue<T>> vals, long timewindow) {
        long now = System.currentTimeMillis()
        long epoch = now-timewindow
        T result = null
        for (TimestampedValue<T> val : vals) {
            if (result == null && val.getTimestamp() > epoch) result = Integer.MAX_VALUE
            if (result == null || (val.getValue() != null && val.getValue() > result)) result = val.getValue()
        }
        return result
    }
    
    /**
     * If the entire time-window is not covered by the given values, then returns Integer.MIN_VALUE
     */
    private <T> T minInWindow(List<TimestampedValue<T>> vals, long timewindow) {
        long now = System.currentTimeMillis()
        T result = null
        long epoch = now-timewindow
        for (TimestampedValue<T> val : vals) {
            if (result == null && val.getTimestamp() > epoch) result = Integer.MIN_VALUE
            if (result == null || (val.getValue() != null && val.getValue() < result)) result = val.getValue()
        }
        return result
    }
    
    @Override
    public String toString() {
        return getClass().getSimpleName() + (name ? "("+name+")" : "")
    }
}
