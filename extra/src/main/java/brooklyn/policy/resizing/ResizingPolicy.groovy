package brooklyn.policy.resizing

import java.util.Map
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.basic.EntityLocal
import brooklyn.entity.trait.Resizable
import brooklyn.event.SensorEvent
import brooklyn.event.SensorEventListener
import brooklyn.policy.basic.AbstractPolicy
import brooklyn.policy.loadbalancing.BalanceableWorkerPool

import com.google.common.base.Preconditions

public class ResizingPolicy extends AbstractPolicy {
    
    private static final Logger LOG = LoggerFactory.getLogger(ResizingPolicy.class)
    
    public static final String POOL_CURRENT_SIZE_KEY = "pool.current.size"
    public static final String POOL_HIGH_THRESHOLD_KEY = "pool.high.threshold"
    public static final String POOL_LOW_THRESHOLD_KEY = "pool.low.threshold"
    public static final String POOL_CURRENT_WORKRATE_KEY = "pool.current.workrate"
    
    
    private Resizable poolEntity
    private ExecutorService executor = Executors.newSingleThreadExecutor()
    private final SensorEventListener<?> eventHandler = new SensorEventListener<Object>() {
        public void onEvent(SensorEvent<?> event) {
            Map<String, ?> properties = (Map<String, ?>) event.getValue()
            switch (event.getSensor()) {
                case BalanceableWorkerPool.POOL_COLD: onPoolCold(properties); break
                case BalanceableWorkerPool.POOL_HOT: onPoolHot(properties); break
            }
        }
    }
    
    private int minPoolSize = 0
    private int maxPoolSize = Integer.MAX_VALUE
    private volatile int desiredPoolSize;
    private AtomicBoolean executorQueued = new AtomicBoolean();
    
    public ResizingPolicy(Map properties = [:]) {
        super(properties)
        if (properties.containsKey("minPoolSize")) minPoolSize = properties.minPoolSize
        if (properties.containsKey("maxPoolSize")) maxPoolSize = properties.maxPoolSize
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
        executor = Executors.newSingleThreadExecutor()
    }
    
    @Override
    public void setEntity(EntityLocal entity) {
        Preconditions.checkArgument(entity instanceof Resizable, "Provided entity must be an instance of Resizable")
        super.setEntity(entity)
        this.poolEntity = (Resizable) entity
        
        subscribe(poolEntity, BalanceableWorkerPool.POOL_COLD, eventHandler)
        subscribe(poolEntity, BalanceableWorkerPool.POOL_HOT, eventHandler)
    }
    
    private void onPoolCold(Map<String, ?> properties) {
        LOG.trace("{} recording pool-cold for {}: {}", this, poolEntity, properties)
        
        int poolCurrentSize = properties.get(POOL_CURRENT_SIZE_KEY)
        double poolCurrentWorkrate = properties.get(POOL_CURRENT_WORKRATE_KEY)
        double poolLowThreshold = properties.get(POOL_LOW_THRESHOLD_KEY)
        
        // Shrink the pool to force its low threshold to fall below the current workrate.
        // NOTE: assumes the pool is homogeneous for now.
        int desiredPoolSize = Math.floor((poolCurrentSize * poolCurrentWorkrate) / poolLowThreshold).intValue()
        desiredPoolSize = toBoundedDesiredPoolSize(desiredPoolSize)
        LOG.trace("{} resizing cold pool {} from {} to {}", this, poolEntity, poolCurrentSize, desiredPoolSize)
        scheduleResize(desiredPoolSize)
    }
    
    private void onPoolHot(Map<String, ?> properties) {
        LOG.trace("{} recording pool-hot for {}: {}", this, poolEntity, properties)
        
        int poolCurrentSize = properties.get(POOL_CURRENT_SIZE_KEY)
        double poolCurrentWorkrate = properties.get(POOL_CURRENT_WORKRATE_KEY)
        double poolHighThreshold = properties.get(POOL_HIGH_THRESHOLD_KEY)
        
        // Grow the pool to force its high threshold to rise above the current workrate.
        // FIXME: assumes the pool is homogeneous for now.
        int desiredPoolSize = Math.ceil((poolCurrentSize * poolCurrentWorkrate) / poolHighThreshold).intValue()
        desiredPoolSize = toBoundedDesiredPoolSize(desiredPoolSize)
        LOG.trace("{} resizing hot pool {} from {} to {}", this, poolEntity, poolCurrentSize, desiredPoolSize)
        new Thread() { void run() { poolEntity.resize(desiredPoolSize) } }.start()
        scheduleResize(desiredPoolSize)
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
        
        if (executorQueued.compareAndSet(false, true)) {
            executor.submit( {
                executorQueued.set(false)
                try {
                    poolEntity.resize(desiredPoolSize)
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt() // gracefully stop
                } catch (Exception e) {
                    if (isRunning()) {
                        LOG.error("Error resizing: "+e, e)
                    } else {
                        LOG.debug("Error resizing, but no longer running: "+e, e)
                    }
                }
            } )
        }
    }
}
