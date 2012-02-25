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
    
    public static final String POOL_CURRENT_SIZE_KEY = "pool.current.size"
    public static final String POOL_HIGH_THRESHOLD_KEY = "pool.high.threshold"
    public static final String POOL_LOW_THRESHOLD_KEY = "pool.low.threshold"
    public static final String POOL_CURRENT_WORKRATE_KEY = "pool.current.workrate"
    
    @SetFromFlag // TODO not respected for policies? I had to look this up in the constructor
    private long minPeriodBetweenExecs = 100
    
    private Entity poolEntity
    
    private volatile ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor()
    private final AtomicBoolean executorQueued = new AtomicBoolean(false)
    private volatile long executorTime = 0
    
    private int minPoolSize = 0
    private int maxPoolSize = Integer.MAX_VALUE
    private final Closure resizeOperator
    private final BasicNotificationSensor poolHotSensor
    private final BasicNotificationSensor poolColdSensor
    
    private volatile int desiredPoolSize;
    
    Closure defaultResizeOperator = { Entity e, int desiredSize ->
        ((Entity)e).resize(desiredSize)
    }
    
    private final SensorEventListener<?> eventHandler = new SensorEventListener<Object>() {
        public void onEvent(SensorEvent<?> event) {
            Map<String, ?> properties = (Map<String, ?>) event.getValue()
            switch (event.getSensor()) {
                case poolColdSensor: onPoolCold(properties); break
                case poolHotSensor: onPoolHot(properties); break
            }
        }
    }
    
    public ResizingPolicy(Map props = [:]) {
        super(props)
        if (props.containsKey("minPoolSize")) minPoolSize = props.minPoolSize
        if (props.containsKey("maxPoolSize")) maxPoolSize = props.maxPoolSize
        resizeOperator = props.resizeOperator ?: defaultResizeOperator
        poolHotSensor = props.poolHotSensor ?: POOL_HOT
        poolColdSensor = props.poolColdSensor ?: POOL_COLD
        minPeriodBetweenExecs = props.minPeriodBetweenExecs ?: 100
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
        }
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
        // TODO perhaps make concurrent calls, rather than waiting for first resize to entirely 
        // finish? On ec2 for example, this can cause us to grow very slowly if first request is for
        // just one new VM to be provisioned.
        
        this.desiredPoolSize = newSize

        if (isRunning() && executorQueued.compareAndSet(false, true)) {
            long now = System.currentTimeMillis()
            long delay = Math.max(0, (executorTime + minPeriodBetweenExecs) - now)
            
            executor.schedule(
                {
                    try {
                        executorTime = System.currentTimeMillis()
                        executorQueued.set(false)
                        
                        resizeOperator.call(poolEntity, desiredPoolSize)
                        
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt() // gracefully stop
                    } catch (Exception e) {
                        if (isRunning()) {
                            LOG.error("Error resizing: "+e, e)
                        } else {
                            if (LOG.isDebugEnabled()) LOG.debug("Error resizing, but no longer running: "+e, e)
                        }
                    }
                },
                delay,
                TimeUnit.MILLISECONDS)
        }
    }
}
