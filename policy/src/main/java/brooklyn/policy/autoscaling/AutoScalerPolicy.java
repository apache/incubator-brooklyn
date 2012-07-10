package brooklyn.policy.autoscaling;

import static brooklyn.util.GroovyJavaMethods.elvis;
import static brooklyn.util.GroovyJavaMethods.truth;
import groovy.lang.Closure;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.trait.Resizable;
import brooklyn.event.Sensor;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.event.basic.BasicNotificationSensor;
import brooklyn.policy.basic.AbstractPolicy;
import brooklyn.policy.loadbalancing.LoadBalancingPolicy;
import brooklyn.util.MutableMap;
import brooklyn.util.TimeWindowedList;
import brooklyn.util.TimestampedValue;
import brooklyn.util.flags.SetFromFlag;
import brooklyn.util.flags.TypeCoercions;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;


/**
 * Policy that is attached to a <code>Resizable</code> entity and dynamically adjusts its size in response to
 * emitted <code>POOL_COLD</code> and <code>POOL_HOT</code> events. (This policy does not itself determine whether
 * the pool is hot or cold, but instead relies on these events being emitted by the monitored entity itself, or
 * by another policy that is attached to it; see, for example, {@link LoadBalancingPolicy}.)
 */
public class AutoScalerPolicy extends AbstractPolicy {
    
    private static final Logger LOG = LoggerFactory.getLogger(AutoScalerPolicy.class);

    // TODO Is there a nicer pattern for registering such type-coercions? 
    // Can't put it in the ResizeOperator interface, nor in core TypeCoercions class because interface is defined in policy/.
    static {
        TypeCoercions.registerAdapter(Closure.class, ResizeOperator.class, new Function<Closure,ResizeOperator>() {
            @Override
            public ResizeOperator apply(final Closure closure) {
                return new ResizeOperator() {
                    @Override public Integer resize(Entity entity, Integer input) {
                        return (Integer) closure.call(entity, input);
                    }
                };
            }
        });
    }
    
    // Pool workrate notifications.
    public static BasicNotificationSensor<Map> POOL_HOT = new BasicNotificationSensor<Map>(
        Map.class, "resizablepool.hot", "Pool is over-utilized; it has insufficient resource for current workload");
    public static BasicNotificationSensor<Map> POOL_COLD = new BasicNotificationSensor<Map>(
        Map.class, "resizablepool.cold", "Pool is under-utilized; it has too much resource for current workload");
    public static BasicNotificationSensor<Map> POOL_OK = new BasicNotificationSensor<Map>(
        Map.class, "resizablepool.cold", "Pool utilization is ok; the available resources are fine for the current workload");

    public static final String POOL_CURRENT_SIZE_KEY = "pool.current.size";
    public static final String POOL_HIGH_THRESHOLD_KEY = "pool.high.threshold";
    public static final String POOL_LOW_THRESHOLD_KEY = "pool.low.threshold";
    public static final String POOL_CURRENT_WORKRATE_KEY = "pool.current.workrate";
    
    @SetFromFlag(defaultVal="100")
    private long minPeriodBetweenExecs;
    
    @SetFromFlag
    private long resizeUpStabilizationDelay;
    
    @SetFromFlag
    private long resizeDownStabilizationDelay;
    
    @SetFromFlag
    private int minPoolSize;
    
    @SetFromFlag(defaultVal="2147483647") // defaultVal=Integer.MAX_VALUE
    private int maxPoolSize;
    
    @SetFromFlag
    private ResizeOperator resizeOperator;
    
    @SetFromFlag
    private Function<Entity,Integer> currentSizeOperator;
    
    @SetFromFlag
    private BasicNotificationSensor poolHotSensor;
    
    @SetFromFlag
    private BasicNotificationSensor poolColdSensor;
    
    @SetFromFlag
    private BasicNotificationSensor poolOkSensor;
    
    private Entity poolEntity;
    
    private volatile ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean executorQueued = new AtomicBoolean(false);
    private volatile long executorTime = 0;
    
    private final TimeWindowedList<Number> recentDesiredResizes;
    
    private final ResizeOperator defaultResizeOperator = new ResizeOperator() {
        public Integer resize(Entity entity, Integer desiredSize) {
            return ((Resizable)entity).resize(desiredSize);
        }
    };
    
    private final Function<Entity,Integer> defaultCurrentSizeOperator = new Function<Entity,Integer>() {
        public Integer apply(Entity entity) {
            return ((Resizable)entity).getCurrentSize();
        }
    };
    
    private final SensorEventListener<Object> eventHandler = new SensorEventListener<Object>() {
        public void onEvent(SensorEvent<Object> event) {
            Map<String, ?> properties = (Map<String, ?>) event.getValue();
            Sensor<?> sensor = event.getSensor();
            
            if (sensor.equals(poolColdSensor)) {
                onPoolCold(properties);
            } else if (sensor.equals(poolHotSensor)) {
                onPoolHot(properties);
            } else if (sensor.equals(poolOkSensor)) {
                onPoolOk(properties);
            }
        }
    };

    public AutoScalerPolicy() {
        this(MutableMap.of());
    }
    public AutoScalerPolicy(Map props) {
        super(props);
        resizeOperator = elvis(resizeOperator, defaultResizeOperator);
        currentSizeOperator = elvis(currentSizeOperator, defaultCurrentSizeOperator);
        poolHotSensor = elvis(poolHotSensor, POOL_HOT);
        poolColdSensor = elvis(poolColdSensor, POOL_COLD);
        poolOkSensor = elvis(poolOkSensor, POOL_OK);
        
        long maxResizeStabilizationDelay = Math.max(resizeUpStabilizationDelay, resizeDownStabilizationDelay);
        recentDesiredResizes = new TimeWindowedList<Number>(MutableMap.of("timePeriod", maxResizeStabilizationDelay, "minExpiredVals", 1));
    }
    
    @Override
    public void suspend() {
        super.suspend();
        // TODO unsubscribe from everything? And resubscribe on resume?
        if (executor != null) executor.shutdownNow();
    }
    
    @Override
    public void resume() {
        super.resume();
        executor = Executors.newSingleThreadScheduledExecutor();
    }
    
    @Override
    public void setEntity(EntityLocal entity) {
        if (resizeOperator == defaultResizeOperator) {
            Preconditions.checkArgument(entity instanceof Resizable, "Provided entity must be an instance of Resizable, because no custom-resizer operator supplied");
        }
        super.setEntity(entity);
        this.poolEntity = entity;
        
        subscribe(poolEntity, poolColdSensor, eventHandler);
        subscribe(poolEntity, poolHotSensor, eventHandler);
        subscribe(poolEntity, poolOkSensor, eventHandler);
    }
    
    private void onPoolCold(Map<String, ?> properties) {
        if (LOG.isTraceEnabled()) LOG.trace("{} recording pool-cold for {}: {}", new Object[] {this, poolEntity, properties});
        
        int poolCurrentSize = (Integer) properties.get(POOL_CURRENT_SIZE_KEY);
        double poolCurrentWorkrate = (Double) properties.get(POOL_CURRENT_WORKRATE_KEY);
        double poolLowThreshold = (Double) properties.get(POOL_LOW_THRESHOLD_KEY);
        
        // Shrink the pool to force its low threshold to fall below the current workrate.
        // NOTE: assumes the pool is homogeneous for now.
        int desiredPoolSize = (int) Math.ceil(poolCurrentWorkrate / (poolLowThreshold/poolCurrentSize));
        desiredPoolSize = toBoundedDesiredPoolSize(desiredPoolSize);
        if (desiredPoolSize < poolCurrentSize) {
            if (LOG.isTraceEnabled()) LOG.trace("{} resizing cold pool {} from {} to {}", new Object[] {this, poolEntity, poolCurrentSize, desiredPoolSize});
            scheduleResize(desiredPoolSize);
        } else {
            if (LOG.isTraceEnabled()) LOG.trace("{} not resizing cold pool {} from {} to {}", new Object[] {this, poolEntity, poolCurrentSize, desiredPoolSize});
            abortResize(poolCurrentSize);
        }

    }
    
    private void onPoolHot(Map<String, ?> properties) {
        if (LOG.isTraceEnabled()) LOG.trace("{} recording pool-hot for {}: {}", new Object[] {this, poolEntity, properties});
        
        int poolCurrentSize = (Integer) properties.get(POOL_CURRENT_SIZE_KEY);
        double poolCurrentWorkrate = (Double) properties.get(POOL_CURRENT_WORKRATE_KEY);
        double poolHighThreshold = (Double) properties.get(POOL_HIGH_THRESHOLD_KEY);
        
        // Grow the pool to force its high threshold to rise above the current workrate.
        // FIXME: assumes the pool is homogeneous for now.
        int desiredPoolSize = (int) Math.ceil(poolCurrentWorkrate / (poolHighThreshold/poolCurrentSize));
        desiredPoolSize = toBoundedDesiredPoolSize(desiredPoolSize);
        if (desiredPoolSize > poolCurrentSize) {
            if (LOG.isTraceEnabled()) LOG.trace("{} resizing hot pool {} from {} to {}", new Object[] {this, poolEntity, poolCurrentSize, desiredPoolSize});
            scheduleResize(desiredPoolSize);
        } else {
            if (LOG.isTraceEnabled()) LOG.trace("{} not resizing hot pool {} from {} to {}", new Object[] {this, poolEntity, poolCurrentSize, desiredPoolSize});
            abortResize(poolCurrentSize);
        }
    }
    
    private void onPoolOk(Map<String, ?> properties) {
        if (LOG.isTraceEnabled()) LOG.trace("{} recording pool-ok for {}: {}", new Object[] {this, poolEntity, properties});
        
        int poolCurrentSize = (Integer) properties.get(POOL_CURRENT_SIZE_KEY);
        
        if (LOG.isTraceEnabled()) LOG.trace("{} not resizing ok pool {} from {}", new Object[] {this, poolEntity, poolCurrentSize});
        abortResize(poolCurrentSize);
    }
    
    private int toBoundedDesiredPoolSize(int size) {
        int result = Math.max(minPoolSize, size);
        result = Math.min(maxPoolSize, result);
        return result;
    }

    /**
     * Schedules a resize, if there is not already a resize operation queued up. When that resize
     * executes, it will resize to whatever the latest value is to be (rather than what it was told
     * to do at the point the job was queued).
     */
    private void scheduleResize(final int newSize) {
        recentDesiredResizes.add(newSize);
        
        scheduleResize();
    }

    private void abortResize(final int currentSize) {
        recentDesiredResizes.add(currentSize);
    }
    
    private void scheduleResize() {
        // TODO perhaps make concurrent calls, rather than waiting for first resize to entirely 
        // finish? On ec2 for example, this can cause us to grow very slowly if first request is for
        // just one new VM to be provisioned.
        
        if (isRunning() && executorQueued.compareAndSet(false, true)) {
            long now = System.currentTimeMillis();
            long delay = Math.max(0, (executorTime + minPeriodBetweenExecs) - now);
            if (LOG.isTraceEnabled()) LOG.trace("{} scheduling resize in {}ms", this, delay);
            
            executor.schedule(new Runnable() {
                @Override public void run() {
                    try {
                        executorTime = System.currentTimeMillis();
                        executorQueued.set(false);

                        long currentPoolSize = currentSizeOperator.apply(poolEntity);
                        CalculatedDesiredPoolSize calculatedDesiredPoolSize = calculateDesiredPoolSize(currentPoolSize);
                        long desiredPoolSize = calculatedDesiredPoolSize.size;
                        boolean stable = calculatedDesiredPoolSize.stable;
                        
                        if (!stable) {
                            // the desired size fluctuations are not stable; ensure we check again later (due to time-window)
                            // even if no additional events have been received
                            if (LOG.isTraceEnabled()) LOG.trace("{} re-scheduling resize check, as desired size not stable; continuing with resize...", 
                                    new Object[] {this, poolEntity, currentPoolSize, desiredPoolSize});
                            scheduleResize();
                        }
                        if (currentPoolSize == desiredPoolSize) {
                            if (LOG.isTraceEnabled()) LOG.trace("{} not resizing pool {} from {} to {}", 
                                    new Object[] {this, poolEntity, currentPoolSize, desiredPoolSize});
                            return;
                        }
                        
                        // TODO Should we use int throughout, rather than casting here?
                        resizeOperator.resize(poolEntity, (int) desiredPoolSize);
                        
                        if (LOG.isDebugEnabled()) LOG.debug("{} requested resize to {}; current {}, min {}, max {}", 
                                new Object[] {this, desiredPoolSize, currentPoolSize, minPoolSize, maxPoolSize});
                        
                    } catch (Exception e) {
                        if (isRunning()) {
                            LOG.error("Error resizing: "+e, e);
                        } else {
                            if (LOG.isDebugEnabled()) LOG.debug("Error resizing, but no longer running: "+e, e);
                        }
                    } catch (Throwable t) {
                        LOG.error("Error resizing: "+t, t);
                        throw Throwables.propagate(t);
                    }
                }},
                delay,
                TimeUnit.MILLISECONDS);
        }
    }
    
    /**
     * Complicated logic for stabilization-delay...
     * Only grow if we have consistently been asked to grow for the resizeUpStabilizationDelay period;
     * Only shrink if we have consistently been asked to shrink for the resizeDownStabilizationDelay period.
     * 
     * @return tuple of desired pool size, and whether this is "stable" (i.e. if we receive no more events 
     *         will this continue to be the desired pool size)
     */
    private CalculatedDesiredPoolSize calculateDesiredPoolSize(long currentPoolSize) {
        long now = System.currentTimeMillis();
        List<TimestampedValue<Number>> downsizeWindowVals = recentDesiredResizes.getValuesInWindow(now, resizeDownStabilizationDelay);
        List<TimestampedValue<Number>> upsizeWindowVals = recentDesiredResizes.getValuesInWindow(now, resizeUpStabilizationDelay);
        long minDesiredPoolSize = maxInWindow(downsizeWindowVals, resizeDownStabilizationDelay).longValue();
        long maxDesiredPoolSize = minInWindow(upsizeWindowVals, resizeUpStabilizationDelay).longValue();
        
        long desiredPoolSize;
        if (currentPoolSize > minDesiredPoolSize) {
            // need to shrink
            desiredPoolSize = minDesiredPoolSize;
        } else if (currentPoolSize < maxDesiredPoolSize) {
            // need to grow
            desiredPoolSize = maxDesiredPoolSize;
        } else {
            desiredPoolSize = currentPoolSize;
        }
        
        boolean stable = (minInWindow(downsizeWindowVals, resizeDownStabilizationDelay).equals(maxInWindow(downsizeWindowVals, resizeDownStabilizationDelay))) &&
                (minInWindow(upsizeWindowVals, resizeUpStabilizationDelay).equals(maxInWindow(upsizeWindowVals, resizeUpStabilizationDelay)));

        if (LOG.isTraceEnabled()) LOG.trace("{} calculated desired pool size: from {} to {}; minDesired {}, maxDesired {}; " +
                "stable {}; now {}; downsizeHistory {}; upsizeHistory {}", 
                new Object[] {this, currentPoolSize, desiredPoolSize, minDesiredPoolSize, maxDesiredPoolSize, stable, now, downsizeWindowVals, upsizeWindowVals});
        
        return new CalculatedDesiredPoolSize(desiredPoolSize, stable);
    }
    
    private static class CalculatedDesiredPoolSize {
        final long size;
        final boolean stable;
        
        CalculatedDesiredPoolSize(long size, boolean stable) {
            this.size = size;
            this.stable = stable;
        }
    }
    
    /**
     * If the entire time-window is not covered by the given values, then returns Integer.MAX_VALUE.
     */
    private <T extends Number> T maxInWindow(List<TimestampedValue<T>> vals, long timewindow) {
        // TODO bad casting from Integer default result to T
        long now = System.currentTimeMillis();
        long epoch = now-timewindow;
        T result = null;
        double resultAsDouble = Integer.MAX_VALUE;
        for (TimestampedValue<T> val : vals) {
            T valAsNum = val.getValue();
            double valAsDouble = (valAsNum != null) ? valAsNum.doubleValue() : 0;
            if (result == null && val.getTimestamp() > epoch) {
                result = (T) Integer.valueOf(Integer.MAX_VALUE);
                resultAsDouble = result.doubleValue();
            }
            if (result == null || (valAsNum != null && valAsDouble > resultAsDouble)) {
                result = valAsNum;
                resultAsDouble = valAsDouble;
            }
        }
        return (T) (result != null ? result : Integer.MAX_VALUE);
    }
    
    /**
     * If the entire time-window is not covered by the given values, then returns Integer.MIN_VALUE
     */
    private <T extends Number> T minInWindow(List<TimestampedValue<T>> vals, long timewindow) {
        long now = System.currentTimeMillis();
        long epoch = now-timewindow;
        T result = null;
        double resultAsDouble = Integer.MIN_VALUE;
        for (TimestampedValue<T> val : vals) {
            T valAsNum = val.getValue();
            double valAsDouble = (valAsNum != null) ? valAsNum.doubleValue() : 0;
            if (result == null && val.getTimestamp() > epoch) {
                result = (T) Integer.valueOf(Integer.MIN_VALUE);
                resultAsDouble = result.doubleValue();
            }
            if (result == null || (val.getValue() != null && valAsDouble < resultAsDouble)) {
                result = valAsNum;
                resultAsDouble = valAsDouble;
            }
        }
        return (T) (result != null ? result : Integer.MIN_VALUE);
    }
    
    @Override
    public String toString() {
        return getClass().getSimpleName() + (truth(name) ? "("+name+")" : "");
    }
}
