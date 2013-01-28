package brooklyn.policy.autoscaling;

import static brooklyn.util.GroovyJavaMethods.elvis;
import static brooklyn.util.GroovyJavaMethods.truth;
import static com.google.common.base.Preconditions.checkNotNull;
import groovy.lang.Closure;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.trait.Resizable;
import brooklyn.entity.trait.Startable;
import brooklyn.event.AttributeSensor;
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
import com.google.common.util.concurrent.ThreadFactoryBuilder;


/**
 * Policy that is attached to a {@link Resizable} entity and dynamically adjusts its size in response to
 * emitted {@code POOL_COLD} and {@code POOL_HOT} events. (This policy does not itself determine whether
 * the pool is hot or cold, but instead relies on these events being emitted by the monitored entity itself, or
 * by another policy that is attached to it; see, for example, {@link LoadBalancingPolicy}.)
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class AutoScalerPolicy extends AbstractPolicy {
    
    private static final Logger LOG = LoggerFactory.getLogger(AutoScalerPolicy.class);

    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String id;
        private String name;
        private AttributeSensor<? extends Number> metric;
        private Entity entityWithMetric;
        private Number metricUpperBound;
        private Number metricLowerBound;
        private int minPoolSize = 0;
        private int maxPoolSize = Integer.MAX_VALUE;
        private long minPeriodBetweenExecs = 100;
        private long resizeUpStabilizationDelay;
        private long resizeDownStabilizationDelay;
        private ResizeOperator resizeOperator;
        private Function<Entity,Integer> currentSizeOperator;
        private BasicNotificationSensor<?> poolHotSensor;
        private BasicNotificationSensor<?> poolColdSensor;
        private BasicNotificationSensor<?> poolOkSensor;

        public Builder id(String val) {
            this.id = val; return this;
        }
        public Builder name(String val) {
            this.name = val; return this;
        }
        public Builder metric(AttributeSensor<? extends Number> val) {
            this.metric = val; return this;
        }
        public Builder entityWithMetric(Entity val) {
            this.entityWithMetric = val; return this;
        }
        public Builder metricLowerBound(Number val) {
            this.metricLowerBound = val; return this;
        }
        public Builder metricUpperBound(Number val) {
            this.metricUpperBound = val; return this;
        }
        public Builder metricRange(Number min, Number max) {
            metricLowerBound = checkNotNull(min);
            metricUpperBound = checkNotNull(max);
            return this;
        }
        public Builder minPoolSize(int val) {
            this.minPoolSize = val; return this;
        }
        public Builder maxPoolSize(int val) {
            this.maxPoolSize = val; return this;
        }
        public Builder sizeRange(int min, int max) {
            minPoolSize = min;
            maxPoolSize = max;
            return this;
        }
        public Builder minPeriodBetweenExecs(long val) {
            this.minPeriodBetweenExecs = val; return this;
        }
        public Builder resizeUpStabilizationDelay(long val) {
            this.resizeUpStabilizationDelay = val; return this;
        }
        public Builder resizeDownStabilizationDelay(long val) {
            this.resizeDownStabilizationDelay = val; return this;
        }
        public Builder resizeOperator(ResizeOperator val) {
            this.resizeOperator = val; return this;
        }
        public Builder currentSizeOperator(Function<Entity, Integer> val) {
            this.currentSizeOperator = val; return this;
        }
        public Builder poolHotSensor(BasicNotificationSensor<?> val) {
            this.poolHotSensor = val; return this;
        }
        public Builder poolColdSensor(BasicNotificationSensor<?> val) {
            this.poolColdSensor = val; return this;
        }
        public Builder poolOkSensor(BasicNotificationSensor<?> val) {
            this.poolOkSensor = val; return this;
        }
        public AutoScalerPolicy build() {
            return new AutoScalerPolicy(toFlags());
        }
        private Map<String,?> toFlags() {
            return MutableMap.<String,Object>builder()
                    .putIfNotNull("id", id)
                    .put("name", name)
                    .put("metric", metric)
                    .put("entityWithMetric", entityWithMetric)
                    .put("metricUpperBound", metricUpperBound)
                    .put("metricLowerBound", metricLowerBound)
                    .put("minPoolSize", minPoolSize)
                    .put("maxPoolSize", maxPoolSize)
                    .put("minPeriodBetweenExecs", minPeriodBetweenExecs)
                    .put("resizeUpStabilizationDelay", resizeUpStabilizationDelay)
                    .put("resizeDownStabilizationDelay", resizeDownStabilizationDelay)
                    .put("resizeOperator", resizeOperator)
                    .put("currentSizeOperator", currentSizeOperator)
                    .put("poolHotSensor", poolHotSensor)
                    .put("poolColdSensor", poolColdSensor)
                    .put("poolOkSensor", poolOkSensor)
                    .build();
        }
    }
    
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
    
    @SetFromFlag
    private AttributeSensor<? extends Number> metric;

    @SetFromFlag
    private Entity entityWithMetric;
    
    @SetFromFlag
    private Number metricLowerBound;
    
    @SetFromFlag
    private Number metricUpperBound;
    
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
    private BasicNotificationSensor<? extends Map> poolHotSensor;
    
    @SetFromFlag
    private BasicNotificationSensor<? extends Map> poolColdSensor;
    
    @SetFromFlag
    private BasicNotificationSensor<? extends Map> poolOkSensor;
    
    private Entity poolEntity;
    
    private final AtomicBoolean executorQueued = new AtomicBoolean(false);
    private volatile long executorTime = 0;
    private volatile ScheduledExecutorService executor;
    
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
    
    private final SensorEventListener<Map> utilizationEventHandler = new SensorEventListener<Map>() {
        public void onEvent(SensorEvent<Map> event) {
            Map<String, ?> properties = (Map<String, ?>) event.getValue();
            Sensor<?> sensor = event.getSensor();
            
            if (sensor.equals(poolColdSensor)) {
                onPoolCold(properties);
            } else if (sensor.equals(poolHotSensor)) {
                onPoolHot(properties);
            } else if (sensor.equals(poolOkSensor)) {
                onPoolOk(properties);
            } else {
                throw new IllegalStateException("Unexpected sensor type: "+sensor+"; event="+event);
            }
        }
    };

    private final SensorEventListener<Number> metricEventHandler = new SensorEventListener<Number>() {
        public void onEvent(SensorEvent<Number> event) {
            assert event.getSensor().equals(metric);
            onMetricChanged(event.getValue());
        }
    };

    public AutoScalerPolicy() {
        this(MutableMap.<String,Object>of());
    }
    
    public AutoScalerPolicy(Map<String,?> props) {
        super(props);
        resizeOperator = elvis(resizeOperator, defaultResizeOperator);
        currentSizeOperator = elvis(currentSizeOperator, defaultCurrentSizeOperator);
        poolHotSensor = elvis(poolHotSensor, POOL_HOT);
        poolColdSensor = elvis(poolColdSensor, POOL_COLD);
        poolOkSensor = elvis(poolOkSensor, POOL_OK);
        
        long maxResizeStabilizationDelay = Math.max(resizeUpStabilizationDelay, resizeDownStabilizationDelay);
        recentDesiredResizes = new TimeWindowedList<Number>(MutableMap.of("timePeriod", maxResizeStabilizationDelay, "minExpiredVals", 1));
        
        // TODO Should re-use the execution manager's thread pool, somehow
        executor = Executors.newSingleThreadScheduledExecutor(newThreadFactory());
    }

    public void setMetricLowerBound(Number val) {
        if (LOG.isInfoEnabled()) LOG.info("{} changing metricLowerBound from {} to {}", new Object[] {this, metricLowerBound, val});
        this.metricLowerBound = checkNotNull(val);
    }
    
    public void setMetricUpperBound(Number val) {
        if (LOG.isInfoEnabled()) LOG.info("{} changing metricUpperBound from {} to {}", new Object[] {this, metricUpperBound, val});
        this.metricUpperBound = checkNotNull(val);
    }
    
    public void setMinPeriodBetweenExecs(long val) {
        if (LOG.isInfoEnabled()) LOG.info("{} changing minPeriodBetweenExecs from {} to {}", new Object[] {this, minPeriodBetweenExecs, val});
        this.minPeriodBetweenExecs = val;
    }
    
    public void setResizeUpStabilizationDelay(long val) {
        if (LOG.isInfoEnabled()) LOG.info("{} changing resizeUpStabilizationDelay from {} to {}", new Object[] {this, resizeUpStabilizationDelay, val});
        this.resizeUpStabilizationDelay = val;
    }
    
    public void setResizeDownStabilizationDelay(long val) {
        if (LOG.isInfoEnabled()) LOG.info("{} changing resizeDownStabilizationDelay from {} to {}", new Object[] {this, resizeDownStabilizationDelay, val});
        this.resizeDownStabilizationDelay = val;
    }
    
    public void setMinPoolSize(int val) {
        if (LOG.isInfoEnabled()) LOG.info("{} changing minPoolSize from {} to {}", new Object[] {this, minPoolSize, val});
        this.minPoolSize = val;
    }
    
    public void setMaxPoolSize(int val) {
        if (LOG.isInfoEnabled()) LOG.info("{} changing maxPoolSize from {} to {}", new Object[] {this, maxPoolSize, val});
        this.maxPoolSize = val;
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
        executor = Executors.newSingleThreadScheduledExecutor(newThreadFactory());
    }
    
    @Override
    public void setEntity(EntityLocal entity) {
        if (resizeOperator == defaultResizeOperator) {
            Preconditions.checkArgument(entity instanceof Resizable, "Provided entity must be an instance of Resizable, because no custom-resizer operator supplied");
        }
        super.setEntity(entity);
        this.poolEntity = entity;
        
        if (metric != null) {
            Entity entityToSubscribeTo = (entityWithMetric != null) ? entityWithMetric : entity;
            subscribe(entityToSubscribeTo, metric, metricEventHandler);
        }
        subscribe(poolEntity, poolColdSensor, utilizationEventHandler);
        subscribe(poolEntity, poolHotSensor, utilizationEventHandler);
        subscribe(poolEntity, poolOkSensor, utilizationEventHandler);
    }
    
    private ThreadFactory newThreadFactory() {
        return new ThreadFactoryBuilder()
                .setNameFormat("brooklyn-autoscalerpolicy-%d")
                .build();
    }
    
    private void onMetricChanged(Number val) {
        if (LOG.isTraceEnabled()) LOG.trace("{} recording pool-metric for {}: {}", new Object[] {this, poolEntity, val});

        double currentMetricD = val.doubleValue();
        double metricUpperBoundD = metricUpperBound.doubleValue();
        double metricLowerBoundD = metricLowerBound.doubleValue();
        int currentSize = currentSizeOperator.apply(entity);
        double currentTotalActivity = currentSize * currentMetricD;
        int desiredSize;
        
        /* We always scale out (modulo stabilization delay) if:
         *   currentTotalActivity > currentSize*metricUpperBound
         * With newDesiredSize the smallest n such that   n*metricUpperBound >= currentTotalActivity
         * ie  n >= currentTotalActiviy/metricUpperBound, thus n := Math.ceil(currentTotalActivity/metricUpperBound)
         * 
         * Else consider scale back if:
         *   currentTotalActivity < currentSize*metricLowerBound
         * With newDesiredSize normally the largest n such that:  
         *   n*metricLowerBound <= currentTotalActivity
         * BUT with an absolute requirement which trumps the above computation
         * that the newDesiredSize doesn't cause immediate scale out:
         *   n*metricUpperBound >= currentTotalActivity
         * thus n := Math.max ( floor(currentTotalActiviy/metricLowerBound), ceil(currentTotal/metricUpperBound) )
         */
        if (currentMetricD > metricUpperBoundD) {
            // scale out
            desiredSize = (int)Math.ceil(currentTotalActivity/metricUpperBoundD);
            desiredSize = toBoundedDesiredPoolSize(desiredSize);
            if (desiredSize > currentSize) {
                if (LOG.isTraceEnabled()) LOG.trace("{} resizing out pool {} from {} to {} ({} > {})", new Object[] {this, poolEntity, currentSize, desiredSize, currentMetricD, metricUpperBoundD});
                scheduleResize(desiredSize);
            } else {
                if (LOG.isTraceEnabled()) LOG.trace("{} not resizing pool {} from {} ({} > {} > {}, but scale-out blocked eg by bounds/check)", new Object[] {this, poolEntity, currentSize, currentMetricD, metricUpperBoundD, metricLowerBoundD});
            }
        } else if (currentMetricD < metricLowerBoundD) {
            // scale back
            desiredSize = (int)Math.floor(currentTotalActivity/metricLowerBoundD);
            desiredSize = toBoundedDesiredPoolSize(desiredSize);
            if (desiredSize < currentTotalActivity/metricUpperBoundD) {
                // this desired size would cause scale-out on next run, ie thrashing, so tweak
                if (LOG.isTraceEnabled()) LOG.trace("{} resizing back pool {} from {}, tweaking from {} to prevent thrashing", new Object[] {this, poolEntity, currentSize, desiredSize });
                desiredSize = (int)Math.ceil(currentTotalActivity/metricUpperBoundD);
                desiredSize = toBoundedDesiredPoolSize(desiredSize);
            }
            if (desiredSize < currentSize) {
                if (LOG.isTraceEnabled()) LOG.trace("{} resizing back pool {} from {} to {} ({} < {})", new Object[] {this, poolEntity, currentSize, desiredSize, currentMetricD, metricLowerBoundD});
                scheduleResize(desiredSize);
            } else {
                if (LOG.isTraceEnabled()) LOG.trace("{} not resizing pool {} from {} ({} < {} < {}, but scale-back blocked eg by bounds/check)", new Object[] {this, poolEntity, currentSize, currentMetricD, metricLowerBoundD, metricUpperBoundD});
            }
        } else {
            if (LOG.isTraceEnabled()) LOG.trace("{} not resizing pool {} from {} ({} within range {}..{})", new Object[] {this, poolEntity, currentSize, currentMetricD, metricLowerBoundD, metricUpperBoundD});
            abortResize(currentSize);
            return; // within a health range; no-op
        }
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

    private boolean isEntityUp() {
        if (entity == null) {
            return false;
        } else if (entity.getEntityType().getSensors().contains(Startable.SERVICE_UP)) {
            return Boolean.TRUE.equals(entity.getAttribute(Startable.SERVICE_UP));
        } else {
            return true;
        }
    }
    
    private void scheduleResize() {
        // TODO perhaps make concurrent calls, rather than waiting for first resize to entirely 
        // finish? On ec2 for example, this can cause us to grow very slowly if first request is for
        // just one new VM to be provisioned.
        
        // Alex comments: yes, for scale out
        
        if (isRunning() && executorQueued.compareAndSet(false, true) && isEntityUp()) {
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
                        
                        // TODO Alex says: I think we should change even if not stable ... worst case we'll shrink later
                        // otherwise if we're at 100 nodes and the num required keeps shifting from 10 to 11 to 8 to 13
                        // we'll always have 100 ... or worse if we have 10 and num required keeps shifting 100 to 101 to 98...
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
                        
                        if (LOG.isDebugEnabled()) LOG.debug("{} requesting resize to {}; current {}, min {}, max {}", 
                                new Object[] {this, desiredPoolSize, currentPoolSize, minPoolSize, maxPoolSize});
                        
                        // TODO Should we use int throughout, rather than casting here?
                        resizeOperator.resize(poolEntity, (int) desiredPoolSize);
                        
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
        // this is the largest size that has been requested in the "stable-for-shrinking" period:
        long minDesiredPoolSize = maxInWindow(downsizeWindowVals, resizeDownStabilizationDelay).longValue();
        // this is the smallest size that has been requested in the "stable-for-growing" period:
        long maxDesiredPoolSize = minInWindow(upsizeWindowVals, resizeUpStabilizationDelay).longValue();
        // (it is a logical consequence of the above that minDesired >= maxDesired -- this is correct, if confusing:
        // think of minDesired as the minimum size we are allowed to resize to, and similarly for maxDesired; 
        // if min > max we can scale to max if current < max, or scale to min if current > min)

        long desiredPoolSize;
        
        boolean stableForShrinking = (minInWindow(downsizeWindowVals, resizeDownStabilizationDelay).equals(maxInWindow(downsizeWindowVals, resizeDownStabilizationDelay)));
        boolean stableForGrowing = (minInWindow(upsizeWindowVals, resizeUpStabilizationDelay).equals(maxInWindow(upsizeWindowVals, resizeUpStabilizationDelay)));
        boolean stable;
        
        if (currentPoolSize < maxDesiredPoolSize) {
            // we have valid request to grow 
            // (we'll never have a valid request to grow and a valid to shrink simultaneously, btw)
            desiredPoolSize = maxDesiredPoolSize;
            stable = stableForGrowing;
        } else if (currentPoolSize > minDesiredPoolSize) {
            // we have valid request to shrink
            desiredPoolSize = minDesiredPoolSize;
            stable = stableForShrinking;
        } else {
            desiredPoolSize = currentPoolSize;
            stable = stableForGrowing && stableForShrinking;
        }

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
