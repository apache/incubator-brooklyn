/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package brooklyn.policy.autoscaling;

import static brooklyn.util.GroovyJavaMethods.truth;
import static com.google.common.base.Preconditions.checkNotNull;
import groovy.lang.Closure;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.catalog.Catalog;
import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.trait.Resizable;
import brooklyn.entity.trait.Startable;
import brooklyn.event.AttributeSensor;
import brooklyn.event.Sensor;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.event.basic.BasicNotificationSensor;
import brooklyn.policy.PolicySpec;
import brooklyn.policy.autoscaling.SizeHistory.WindowSummary;
import brooklyn.policy.basic.AbstractPolicy;
import brooklyn.policy.loadbalancing.LoadBalancingPolicy;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.flags.SetFromFlag;
import brooklyn.util.flags.TypeCoercions;
import brooklyn.util.time.Duration;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.reflect.TypeToken;
import com.google.common.util.concurrent.ThreadFactoryBuilder;


/**
 * Policy that is attached to a {@link Resizable} entity and dynamically adjusts its size in response to
 * emitted {@code POOL_COLD} and {@code POOL_HOT} events. (This policy does not itself determine whether
 * the pool is hot or cold, but instead relies on these events being emitted by the monitored entity itself, or
 * by another policy that is attached to it; see, for example, {@link LoadBalancingPolicy}.)
 */
@SuppressWarnings({"rawtypes", "unchecked"})
@Catalog
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
        private BasicNotificationSensor<? super MaxPoolSizeReachedEvent> maxSizeReachedSensor;
        private long maxReachedNotificationDelay;
        
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
        public Builder maxSizeReachedSensor(BasicNotificationSensor<? super MaxPoolSizeReachedEvent> val) {
            this.maxSizeReachedSensor = val; return this;
        }
        public Builder maxReachedNotificationDelay(long val) {
            this.maxReachedNotificationDelay = val; return this;
        }
        public AutoScalerPolicy build() {
            return new AutoScalerPolicy(toFlags());
        }
        public PolicySpec<AutoScalerPolicy> buildSpec() {
            return PolicySpec.create(AutoScalerPolicy.class)
                    .configure(toFlags());
        }
        private Map<String,?> toFlags() {
            return MutableMap.<String,Object>builder()
                    .putIfNotNull("id", id)
                    .putIfNotNull("name", name)
                    .putIfNotNull("metric", metric)
                    .putIfNotNull("entityWithMetric", entityWithMetric)
                    .putIfNotNull("metricUpperBound", metricUpperBound)
                    .putIfNotNull("metricLowerBound", metricLowerBound)
                    .putIfNotNull("minPoolSize", minPoolSize)
                    .putIfNotNull("maxPoolSize", maxPoolSize)
                    .putIfNotNull("minPeriodBetweenExecs", minPeriodBetweenExecs)
                    .putIfNotNull("resizeUpStabilizationDelay", resizeUpStabilizationDelay)
                    .putIfNotNull("resizeDownStabilizationDelay", resizeDownStabilizationDelay)
                    .putIfNotNull("resizeOperator", resizeOperator)
                    .putIfNotNull("currentSizeOperator", currentSizeOperator)
                    .putIfNotNull("poolHotSensor", poolHotSensor)
                    .putIfNotNull("poolColdSensor", poolColdSensor)
                    .putIfNotNull("poolOkSensor", poolOkSensor)
                    .putIfNotNull("maxSizeReachedSensor", maxSizeReachedSensor)
                    .putIfNotNull("maxReachedNotificationDelay", maxReachedNotificationDelay)
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
    public static BasicNotificationSensor<Map> DEFAULT_POOL_HOT_SENSOR = new BasicNotificationSensor<Map>(
        Map.class, "resizablepool.hot", "Pool is over-utilized; it has insufficient resource for current workload");
    public static BasicNotificationSensor<Map> DEFAULT_POOL_COLD_SENSOR = new BasicNotificationSensor<Map>(
        Map.class, "resizablepool.cold", "Pool is under-utilized; it has too much resource for current workload");
    public static BasicNotificationSensor<Map> DEFAULT_POOL_OK_SENSOR = new BasicNotificationSensor<Map>(
        Map.class, "resizablepool.cold", "Pool utilization is ok; the available resources are fine for the current workload");

    /**
     * A convenience for policies that want to register a {@code builder.maxSizeReachedSensor(sensor)}.
     * Note that this "default" is not set automatically; the default is for no sensor to be used (so
     * no events emitted).
     */
    public static BasicNotificationSensor<MaxPoolSizeReachedEvent> DEFAULT_MAX_SIZE_REACHED_SENSOR = new BasicNotificationSensor<MaxPoolSizeReachedEvent>(
            MaxPoolSizeReachedEvent.class, "resizablepool.maxSizeReached", "Consistently wanted to resize the pool above the max allowed size");

    public static final String POOL_CURRENT_SIZE_KEY = "pool.current.size";
    public static final String POOL_HIGH_THRESHOLD_KEY = "pool.high.threshold";
    public static final String POOL_LOW_THRESHOLD_KEY = "pool.low.threshold";
    public static final String POOL_CURRENT_WORKRATE_KEY = "pool.current.workrate";
    
    @SetFromFlag("metric")
    public static final ConfigKey<AttributeSensor<? extends Number>> METRIC = BasicConfigKey.builder(new TypeToken<AttributeSensor<? extends Number>>() {})
            .name("autoscaler.metric")
            .build();

    @SetFromFlag("entityWithMetric")
    public static final ConfigKey<Entity> ENTITY_WITH_METRIC = BasicConfigKey.builder(Entity.class)
            .name("autoscaler.entityWithMetric")
            .build();
    
    @SetFromFlag("metricLowerBound")
    public static final ConfigKey<Number> METRIC_LOWER_BOUND = BasicConfigKey.builder(Number.class)
            .name("autoscaler.metricLowerBound")
            .reconfigurable(true)
            .build();
    
    @SetFromFlag("metricUpperBound")
    public static final ConfigKey<Number> METRIC_UPPER_BOUND = BasicConfigKey.builder(Number.class)
            .name("autoscaler.metricUpperBound")
            .reconfigurable(true)
            .build();
    
    @SetFromFlag("minPeriodBetweenExecs")
    public static final ConfigKey<Duration> MIN_PERIOD_BETWEEN_EXECS = BasicConfigKey.builder(Duration.class)
            .name("autoscaler.minPeriodBetweenExecs")
            .defaultValue(Duration.millis(100))
            .build();
    
    @SetFromFlag("resizeUpStabilizationDelay")
    public static final ConfigKey<Duration> RESIZE_UP_STABILIZATION_DELAY = BasicConfigKey.builder(Duration.class)
            .name("autoscaler.resizeUpStabilizationDelay")
            .defaultValue(Duration.ZERO)
            .reconfigurable(true)
            .build();
    
    @SetFromFlag("resizeDownStabilizationDelay")
    public static final ConfigKey<Duration> RESIZE_DOWN_STABILIZATION_DELAY = BasicConfigKey.builder(Duration.class)
            .name("autoscaler.resizeDownStabilizationDelay")
            .defaultValue(Duration.ZERO)
            .reconfigurable(true)
            .build();

    @SetFromFlag("minPoolSize")
    public static final ConfigKey<Integer> MIN_POOL_SIZE = BasicConfigKey.builder(Integer.class)
            .name("autoscaler.minPoolSize")
            .defaultValue(0)
            .reconfigurable(true)
            .build();
    
    @SetFromFlag("maxPoolSize")
    public static final ConfigKey<Integer> MAX_POOL_SIZE = BasicConfigKey.builder(Integer.class)
            .name("autoscaler.maxPoolSize")
            .defaultValue(Integer.MAX_VALUE)
            .reconfigurable(true)
            .build();

    @SetFromFlag("resizeOperator")
    public static final ConfigKey<ResizeOperator> RESIZE_OPERATOR = BasicConfigKey.builder(ResizeOperator.class)
            .name("autoscaler.resizeOperator")
            .defaultValue(new ResizeOperator() {
                    public Integer resize(Entity entity, Integer desiredSize) {
                        return ((Resizable)entity).resize(desiredSize);
                    }})
            .build();
    
    @SetFromFlag("currentSizeOperator")
    public static final ConfigKey<Function<Entity,Integer>> CURRENT_SIZE_OPERATOR = BasicConfigKey.builder(new TypeToken<Function<Entity,Integer>>() {})
            .name("autoscaler.currentSizeOperator")
            .defaultValue(new Function<Entity,Integer>() {
                    public Integer apply(Entity entity) {
                        return ((Resizable)entity).getCurrentSize();
                    }})
            .build();

    @SetFromFlag("poolHotSensor")
    public static final ConfigKey<BasicNotificationSensor<? extends Map>> POOL_HOT_SENSOR = BasicConfigKey.builder(new TypeToken<BasicNotificationSensor<? extends Map>>() {})
            .name("autoscaler.poolHotSensor")
            .defaultValue(DEFAULT_POOL_HOT_SENSOR)
            .build();

    @SetFromFlag("poolColdSensor")
    public static final ConfigKey<BasicNotificationSensor<? extends Map>> POOL_COLD_SENSOR = BasicConfigKey.builder(new TypeToken<BasicNotificationSensor<? extends Map>>() {})
            .name("autoscaler.poolColdSensor")
            .defaultValue(DEFAULT_POOL_COLD_SENSOR)
            .build();

    @SetFromFlag("poolOkSensor")
    public static final ConfigKey<BasicNotificationSensor<? extends Map>> POOL_OK_SENSOR = BasicConfigKey.builder(new TypeToken<BasicNotificationSensor<? extends Map>>() {})
            .name("autoscaler.poolOkSensor")
            .defaultValue(DEFAULT_POOL_OK_SENSOR)
            .build();

    @SetFromFlag("maxSizeReachedSensor")
    public static final ConfigKey<BasicNotificationSensor<? super MaxPoolSizeReachedEvent>> MAX_SIZE_REACHED_SENSOR = BasicConfigKey.builder(new TypeToken<BasicNotificationSensor<? super MaxPoolSizeReachedEvent>>() {})
            .name("autoscaler.maxSizeReachedSensor")
            .description("Sensor for which a notification will be emitted (on the associated entity) when we consistently wanted to resize the pool above the max allowed size, for maxReachedNotificationDelay milliseconds")
            .build();
    
    @SetFromFlag("maxReachedNotificationDelay")
    public static final ConfigKey<Duration> MAX_REACHED_NOTIFICATION_DELAY = BasicConfigKey.builder(Duration.class)
            .name("autoscaler.maxReachedNotificationDelay")
            .description("Time that we consistently wanted to go above the maxPoolSize for, after which the maxSizeReachedSensor (if any) will be emitted")
            .defaultValue(Duration.ZERO)
            .build();
    
    private Entity poolEntity;
    
    private final AtomicBoolean executorQueued = new AtomicBoolean(false);
    private volatile long executorTime = 0;
    private volatile ScheduledExecutorService executor;

    private SizeHistory recentUnboundedResizes;

    private SizeHistory recentDesiredResizes;
    
    private long maxReachedLastNotifiedTime;
    
    private final SensorEventListener<Map> utilizationEventHandler = new SensorEventListener<Map>() {
        public void onEvent(SensorEvent<Map> event) {
            Map<String, ?> properties = (Map<String, ?>) event.getValue();
            Sensor<?> sensor = event.getSensor();
            
            if (sensor.equals(getPoolColdSensor())) {
                onPoolCold(properties);
            } else if (sensor.equals(getPoolHotSensor())) {
                onPoolHot(properties);
            } else if (sensor.equals(getPoolOkSensor())) {
                onPoolOk(properties);
            } else {
                throw new IllegalStateException("Unexpected sensor type: "+sensor+"; event="+event);
            }
        }
    };

    private final SensorEventListener<Number> metricEventHandler = new SensorEventListener<Number>() {
        public void onEvent(SensorEvent<Number> event) {
            assert event.getSensor().equals(getMetric());
            onMetricChanged(event.getValue());
        }
    };

    public AutoScalerPolicy() {
        this(MutableMap.<String,Object>of());
    }
    
    public AutoScalerPolicy(Map<String,?> props) {
        super(props);
    }

    @Override
    public void init() {
        doInit();
    }

    @Override
    public void rebind() {
        doInit();
    }
    
    protected void doInit() {
        long maxReachedNotificationDelay = getMaxReachedNotificationDelay().toMilliseconds();
        recentUnboundedResizes = new SizeHistory(maxReachedNotificationDelay);
        
        long maxResizeStabilizationDelay = Math.max(getResizeUpStabilizationDelay().toMilliseconds(), getResizeDownStabilizationDelay().toMilliseconds());
        recentDesiredResizes = new SizeHistory(maxResizeStabilizationDelay);
        
        // TODO Should re-use the execution manager's thread pool, somehow
        executor = Executors.newSingleThreadScheduledExecutor(newThreadFactory());
    }

    public void setMetricLowerBound(Number val) {
        if (LOG.isInfoEnabled()) LOG.info("{} changing metricLowerBound from {} to {}", new Object[] {this, getMetricLowerBound(), val});
        setConfig(METRIC_LOWER_BOUND, checkNotNull(val));
    }
    
    public void setMetricUpperBound(Number val) {
        if (LOG.isInfoEnabled()) LOG.info("{} changing metricUpperBound from {} to {}", new Object[] {this, getMetricUpperBound(), val});
        setConfig(METRIC_UPPER_BOUND, checkNotNull(val));
    }
    
    /**
     * @deprecated since 0.7.0; use {@link #setMinPeriodBetweenExecs(Duration)}
     */
    public void setMinPeriodBetweenExecs(long val) {
        setMinPeriodBetweenExecs(Duration.millis(val));
    }

    public void setMinPeriodBetweenExecs(Duration val) {
        if (LOG.isInfoEnabled()) LOG.info("{} changing minPeriodBetweenExecs from {} to {}", new Object[] {this, getMinPeriodBetweenExecs(), val});
        setConfig(MIN_PERIOD_BETWEEN_EXECS, val);
    }

    /**
     * @deprecated since 0.7.0; use {@link #setResizeDownStabilizationDelay(Duration)}
     */
    public void setResizeUpStabilizationDelay(long val) {
        setResizeUpStabilizationDelay(Duration.millis(val));
    }
    
    public void setResizeUpStabilizationDelay(Duration val) {
        if (LOG.isInfoEnabled()) LOG.info("{} changing resizeUpStabilizationDelay from {} to {}", new Object[] {this, getResizeUpStabilizationDelay(), val});
        setConfig(RESIZE_UP_STABILIZATION_DELAY, val);
    }
    
    /**
     * @deprecated since 0.7.0; use {@link #setResizeDownStabilizationDelay(Duration)}
     */
    public void setResizeDownStabilizationDelay(long val) {
        setResizeDownStabilizationDelay(Duration.millis(val));
    }
    
    public void setResizeDownStabilizationDelay(Duration val) {
        if (LOG.isInfoEnabled()) LOG.info("{} changing resizeDownStabilizationDelay from {} to {}", new Object[] {this, getResizeDownStabilizationDelay(), val});
        setConfig(RESIZE_DOWN_STABILIZATION_DELAY, val);
    }
    
    public void setMinPoolSize(int val) {
        if (LOG.isInfoEnabled()) LOG.info("{} changing minPoolSize from {} to {}", new Object[] {this, getMinPoolSize(), val});
        setConfig(MIN_POOL_SIZE, val);
    }
    
    public void setMaxPoolSize(int val) {
        if (LOG.isInfoEnabled()) LOG.info("{} changing maxPoolSize from {} to {}", new Object[] {this, getMaxPoolSize(), val});
        setConfig(MAX_POOL_SIZE, val);
    }
    
    private AttributeSensor<? extends Number> getMetric() {
        return getConfig(METRIC);
    }

    private Entity getEntityWithMetric() {
        return getConfig(ENTITY_WITH_METRIC);
    }
    
    private Number getMetricLowerBound() {
        return getConfig(METRIC_LOWER_BOUND);
    }
    
    private Number getMetricUpperBound() {
        return getConfig(METRIC_UPPER_BOUND);
    }
    
    private Duration getMinPeriodBetweenExecs() {
        return getConfig(MIN_PERIOD_BETWEEN_EXECS);
    }
    
    private Duration getResizeUpStabilizationDelay() {
        return getConfig(RESIZE_UP_STABILIZATION_DELAY);
    }
    
    private Duration getResizeDownStabilizationDelay() {
        return getConfig(RESIZE_DOWN_STABILIZATION_DELAY);
    }
    
    private int getMinPoolSize() {
        return getConfig(MIN_POOL_SIZE);
    }
    
    private int getMaxPoolSize() {
        return getConfig(MAX_POOL_SIZE);
    }
    
    private ResizeOperator getResizeOperator() {
        return getConfig(RESIZE_OPERATOR);
    }
    
    private Function<Entity,Integer> getCurrentSizeOperator() {
        return getConfig(CURRENT_SIZE_OPERATOR);
    }
    
    private BasicNotificationSensor<? extends Map> getPoolHotSensor() {
        return getConfig(POOL_HOT_SENSOR);
    }
    
    private BasicNotificationSensor<? extends Map> getPoolColdSensor() {
        return getConfig(POOL_COLD_SENSOR);
    }
    
    private BasicNotificationSensor<? extends Map> getPoolOkSensor() {
        return getConfig(POOL_OK_SENSOR);
    }
    
    private BasicNotificationSensor<? super MaxPoolSizeReachedEvent> getMaxSizeReachedSensor() {
        return getConfig(MAX_SIZE_REACHED_SENSOR);
    }
    
    private Duration getMaxReachedNotificationDelay() {
        return getConfig(MAX_REACHED_NOTIFICATION_DELAY);
    }

    @Override
    protected <T> void doReconfigureConfig(ConfigKey<T> key, T val) {
        if (key.equals(RESIZE_UP_STABILIZATION_DELAY)) {
            Duration maxResizeStabilizationDelay = Duration.max((Duration)val, getResizeDownStabilizationDelay());
            recentDesiredResizes.setWindowSize(maxResizeStabilizationDelay);
        } else if (key.equals(RESIZE_DOWN_STABILIZATION_DELAY)) {
            Duration maxResizeStabilizationDelay = Duration.max((Duration)val, getResizeUpStabilizationDelay());
            recentDesiredResizes.setWindowSize(maxResizeStabilizationDelay);
        } else if (key.equals(METRIC_LOWER_BOUND)) {
            // TODO If recorded what last metric value was then we could recalculate immediately
            // Rely on next metric-change to trigger recalculation; 
            // and same for those below...
        } else if (key.equals(METRIC_UPPER_BOUND)) {

        } else if (key.equals(MIN_POOL_SIZE)) {
            int newMin = (Integer) val;
            if (newMin > getConfig(MAX_POOL_SIZE)) {
                throw new IllegalArgumentException("Min pool size "+val+" must not be greater than max pool size "+getConfig(MAX_POOL_SIZE));
            }
            onPoolSizeLimitsChanged(newMin, getConfig(MAX_POOL_SIZE));
        } else if (key.equals(MAX_POOL_SIZE)) {
            int newMax = (Integer) val;
            if (newMax < getConfig(MIN_POOL_SIZE)) {
                throw new IllegalArgumentException("Min pool size "+val+" must not be greater than max pool size "+getConfig(MAX_POOL_SIZE));
            }
            onPoolSizeLimitsChanged(getConfig(MIN_POOL_SIZE), newMax);
        } else {
            throw new UnsupportedOperationException("reconfiguring "+key+" unsupported for "+this);
        }
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
        if (!configsInternal.getConfigRaw(RESIZE_OPERATOR, true).isPresentAndNonNull()) {
            Preconditions.checkArgument(entity instanceof Resizable, "Provided entity must be an instance of Resizable, because no custom-resizer operator supplied");
        }
        super.setEntity(entity);
        this.poolEntity = entity;
        
        if (getMetric() != null) {
            Entity entityToSubscribeTo = (getEntityWithMetric() != null) ? getEntityWithMetric() : entity;
            subscribe(entityToSubscribeTo, getMetric(), metricEventHandler);
        }
        subscribe(poolEntity, getPoolColdSensor(), utilizationEventHandler);
        subscribe(poolEntity, getPoolHotSensor(), utilizationEventHandler);
        subscribe(poolEntity, getPoolOkSensor(), utilizationEventHandler);
    }
    
    private ThreadFactory newThreadFactory() {
        return new ThreadFactoryBuilder()
                .setNameFormat("brooklyn-autoscalerpolicy-%d")
                .build();
    }

    /**
     * Forces an immediate resize (without waiting for stabilization etc) if the current size is 
     * not within the min and max limits. We schedule this so that all resize operations are done
     * by the same thread.
     */
    private void onPoolSizeLimitsChanged(final int min, final int max) {
        if (LOG.isTraceEnabled()) LOG.trace("{} checking pool size on limits changed for {} (between {} and {})", new Object[] {this, poolEntity, min, max});
        
        if (isRunning() && isEntityUp()) {
            executor.submit(new Runnable() {
                @Override public void run() {
                    try {
                        int currentSize = getCurrentSizeOperator().apply(entity);
                        int desiredSize = Math.min(max, Math.max(min, currentSize));

                        if (currentSize != desiredSize) {
                            if (LOG.isInfoEnabled()) LOG.info("{} resizing pool {} immediateley from {} to {} (due to new pool size limits)", new Object[] {this, poolEntity, currentSize, desiredSize});
                            getResizeOperator().resize(poolEntity, desiredSize);
                        }
                        
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
                }});
        }
    }

    private void onMetricChanged(Number val) {
        if (LOG.isTraceEnabled()) LOG.trace("{} recording pool-metric for {}: {}", new Object[] {this, poolEntity, val});

        if (val==null) {
            // occurs e.g. if using an aggregating enricher who returns null when empty, the sensor has gone away
            if (LOG.isTraceEnabled()) LOG.trace("{} not resizing pool {}, inbound metric is null", new Object[] {this, poolEntity});
            return;
        }
        
        double currentMetricD = val.doubleValue();
        double metricUpperBoundD = getMetricUpperBound().doubleValue();
        double metricLowerBoundD = getMetricLowerBound().doubleValue();
        int currentSize = getCurrentSizeOperator().apply(entity);
        double currentTotalActivity = currentSize * currentMetricD;
        int unboundedSize;
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
            unboundedSize = (int)Math.ceil(currentTotalActivity/metricUpperBoundD);
            desiredSize = toBoundedDesiredPoolSize(unboundedSize);
            if (desiredSize > currentSize) {
                if (LOG.isTraceEnabled()) LOG.trace("{} resizing out pool {} from {} to {} ({} > {})", new Object[] {this, poolEntity, currentSize, desiredSize, currentMetricD, metricUpperBoundD});
                scheduleResize(desiredSize);
            } else {
                if (LOG.isTraceEnabled()) LOG.trace("{} not resizing pool {} from {} ({} > {} > {}, but scale-out blocked eg by bounds/check)", new Object[] {this, poolEntity, currentSize, currentMetricD, metricUpperBoundD, metricLowerBoundD});
            }
            onNewUnboundedPoolSize(unboundedSize);
            
        } else if (currentMetricD < metricLowerBoundD) {
            // scale back
            unboundedSize = (int)Math.floor(currentTotalActivity/metricLowerBoundD);
            desiredSize = toBoundedDesiredPoolSize(unboundedSize);
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
            onNewUnboundedPoolSize(unboundedSize);
            
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
        int unboundedPoolSize = (int) Math.ceil(poolCurrentWorkrate / (poolLowThreshold/poolCurrentSize));
        int desiredPoolSize = toBoundedDesiredPoolSize(unboundedPoolSize);
        
        if (desiredPoolSize < poolCurrentSize) {
            if (LOG.isTraceEnabled()) LOG.trace("{} resizing cold pool {} from {} to {}", new Object[] {this, poolEntity, poolCurrentSize, desiredPoolSize});
            scheduleResize(desiredPoolSize);
        } else {
            if (LOG.isTraceEnabled()) LOG.trace("{} not resizing cold pool {} from {} to {}", new Object[] {this, poolEntity, poolCurrentSize, desiredPoolSize});
            abortResize(poolCurrentSize);
        }
        
        onNewUnboundedPoolSize(unboundedPoolSize);
    }
    
    private void onPoolHot(Map<String, ?> properties) {
        if (LOG.isTraceEnabled()) LOG.trace("{} recording pool-hot for {}: {}", new Object[] {this, poolEntity, properties});
        
        int poolCurrentSize = (Integer) properties.get(POOL_CURRENT_SIZE_KEY);
        double poolCurrentWorkrate = (Double) properties.get(POOL_CURRENT_WORKRATE_KEY);
        double poolHighThreshold = (Double) properties.get(POOL_HIGH_THRESHOLD_KEY);
        
        // Grow the pool to force its high threshold to rise above the current workrate.
        // FIXME: assumes the pool is homogeneous for now.
        int unboundedPoolSize = (int) Math.ceil(poolCurrentWorkrate / (poolHighThreshold/poolCurrentSize));
        int desiredPoolSize = toBoundedDesiredPoolSize(unboundedPoolSize);
        if (desiredPoolSize > poolCurrentSize) {
            if (LOG.isTraceEnabled()) LOG.trace("{} resizing hot pool {} from {} to {}", new Object[] {this, poolEntity, poolCurrentSize, desiredPoolSize});
            scheduleResize(desiredPoolSize);
        } else {
            if (LOG.isTraceEnabled()) LOG.trace("{} not resizing hot pool {} from {} to {}", new Object[] {this, poolEntity, poolCurrentSize, desiredPoolSize});
            abortResize(poolCurrentSize);
        }
        onNewUnboundedPoolSize(unboundedPoolSize);
    }
    
    private void onPoolOk(Map<String, ?> properties) {
        if (LOG.isTraceEnabled()) LOG.trace("{} recording pool-ok for {}: {}", new Object[] {this, poolEntity, properties});
        
        int poolCurrentSize = (Integer) properties.get(POOL_CURRENT_SIZE_KEY);
        
        if (LOG.isTraceEnabled()) LOG.trace("{} not resizing ok pool {} from {}", new Object[] {this, poolEntity, poolCurrentSize});
        abortResize(poolCurrentSize);
    }
    
    private int toBoundedDesiredPoolSize(int size) {
        int result = Math.max(getMinPoolSize(), size);
        result = Math.min(getMaxPoolSize(), result);
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

    /**
     * If a listener is registered to be notified of the max-pool-size cap being reached, then record
     * what our unbounded size would be and schedule a check to see if this unbounded size is sustained.
     * 
     * Piggie backs off the existing scheduleResize execution, which now also checks if the listener
     * needs to be called.
     */
    private void onNewUnboundedPoolSize(final int val) {
        if (getMaxSizeReachedSensor() != null) {
            recentUnboundedResizes.add(val);
            scheduleResize();
        }
    }
    
    private void abortResize(final int currentSize) {
        recentDesiredResizes.add(currentSize);
        recentUnboundedResizes.add(currentSize);
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
        // TODO Make scale-out calls concurrent, rather than waiting for first resize to entirely 
        // finish. On ec2 for example, this can cause us to grow very slowly if first request is for
        // just one new VM to be provisioned.
        
        if (isRunning() && isEntityUp() && executorQueued.compareAndSet(false, true)) {
            long now = System.currentTimeMillis();
            long delay = Math.max(0, (executorTime + getMinPeriodBetweenExecs().toMilliseconds()) - now);
            if (LOG.isTraceEnabled()) LOG.trace("{} scheduling resize in {}ms", this, delay);
            
            executor.schedule(new Runnable() {
                @Override public void run() {
                    try {
                        executorTime = System.currentTimeMillis();
                        executorQueued.set(false);

                        resizeNow();
                        notifyMaxReachedIfRequiredNow();
                        
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
     * Looks at the values for "unbounded pool size" (i.e. if we ignore caps of minSize and maxSize) to report what
     * those values have been within a time window. The time window used is the "maxReachedNotificationDelay",
     * which determines how many milliseconds after being consistently above the max-size will it take before
     * we emit the sensor event (if any).
     */
    private void notifyMaxReachedIfRequiredNow() {
        BasicNotificationSensor<? super MaxPoolSizeReachedEvent> maxSizeReachedSensor = getMaxSizeReachedSensor();
        if (maxSizeReachedSensor == null) {
            return;
        }
        
        WindowSummary valsSummary = recentUnboundedResizes.summarizeWindow(getMaxReachedNotificationDelay());
        long timeWindowSize = getMaxReachedNotificationDelay().toMilliseconds();
        long currentPoolSize = getCurrentSizeOperator().apply(poolEntity);
        int maxAllowedPoolSize = getMaxPoolSize();
        long unboundedSustainedMaxPoolSize = valsSummary.min; // The sustained maximum (i.e. the smallest it's dropped down to)
        long unboundedCurrentPoolSize = valsSummary.latest;
        
        if (maxReachedLastNotifiedTime > 0) {
            // already notified the listener; don't do it again
            // TODO Could have max period for notifications, or a step increment to warn when exceeded by ever bigger amounts
            
        } else if (unboundedSustainedMaxPoolSize > maxAllowedPoolSize) {
            // We have consistently wanted to be bigger than the max allowed; tell the listener
            if (LOG.isDebugEnabled()) LOG.debug("{} notifying listener of max pool size reached; current {}, max {}, unbounded current {}, unbounded max {}", 
                    new Object[] {this, currentPoolSize, maxAllowedPoolSize, unboundedCurrentPoolSize, unboundedSustainedMaxPoolSize});
            
            maxReachedLastNotifiedTime = System.currentTimeMillis();
            MaxPoolSizeReachedEvent event = MaxPoolSizeReachedEvent.builder()
                    .currentPoolSize(currentPoolSize)
                    .maxAllowed(maxAllowedPoolSize)
                    .currentUnbounded(unboundedCurrentPoolSize)
                    .maxUnbounded(unboundedSustainedMaxPoolSize)
                    .timeWindow(timeWindowSize)
                    .build();
            entity.emit(maxSizeReachedSensor, event);
            
        } else if (valsSummary.max > maxAllowedPoolSize) {
            // We temporarily wanted to be bigger than the max allowed; check back later to see if consistent
            // TODO Could check if there has been anything bigger than "min" since min happened (would be more efficient)
            if (LOG.isTraceEnabled()) LOG.trace("{} re-scheduling max-reached check for {}, as unbounded size not stable (min {}, max {}, latest {})", 
                    new Object[] {this, poolEntity, valsSummary.min, valsSummary.max, valsSummary.latest});
            scheduleResize();
            
        } else {
            // nothing to write home about; continually below maxAllowed
        }
    }

    private void resizeNow() {
        long currentPoolSize = getCurrentSizeOperator().apply(poolEntity);
        CalculatedDesiredPoolSize calculatedDesiredPoolSize = calculateDesiredPoolSize(currentPoolSize);
        long desiredPoolSize = calculatedDesiredPoolSize.size;
        boolean stable = calculatedDesiredPoolSize.stable;
        
        if (!stable) {
            // the desired size fluctuations are not stable; ensure we check again later (due to time-window)
            // even if no additional events have been received
            // (note we continue now with as "good" a resize as we can given the instability)
            if (LOG.isTraceEnabled()) LOG.trace("{} re-scheduling resize check for {}, as desired size not stable (current {}, desired {}); continuing with resize...", 
                    new Object[] {this, poolEntity, currentPoolSize, desiredPoolSize});
            scheduleResize();
        }
        if (currentPoolSize == desiredPoolSize) {
            if (LOG.isTraceEnabled()) LOG.trace("{} not resizing pool {} from {} to {}", 
                    new Object[] {this, poolEntity, currentPoolSize, desiredPoolSize});
            return;
        }
        
        if (LOG.isDebugEnabled()) LOG.debug("{} requesting resize to {}; current {}, min {}, max {}", 
                new Object[] {this, desiredPoolSize, currentPoolSize, getMinPoolSize(), getMaxPoolSize()});
        
        // TODO Should we use int throughout, rather than casting here?
        getResizeOperator().resize(poolEntity, (int) desiredPoolSize);
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
        WindowSummary downsizeSummary = recentDesiredResizes.summarizeWindow(getResizeDownStabilizationDelay());
        WindowSummary upsizeSummary = recentDesiredResizes.summarizeWindow(getResizeUpStabilizationDelay());
        
        // this is the _sustained_ growth value; the smallest size that has been requested in the "stable-for-growing" period
        long maxDesiredPoolSize = upsizeSummary.min;
        boolean stableForGrowing = upsizeSummary.stableForGrowth;
        
        // this is the _sustained_ shrink value; largest size that has been requested in the "stable-for-shrinking" period:
        long minDesiredPoolSize = downsizeSummary.max;
        boolean stableForShrinking = downsizeSummary.stableForShrinking;
        
        // (it is a logical consequence of the above that minDesired >= maxDesired -- this is correct, if confusing:
        // think of minDesired as the minimum size we are allowed to resize to, and similarly for maxDesired; 
        // if min > max we can scale to max if current < max, or scale to min if current > min)

        long desiredPoolSize;
        
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
                new Object[] {this, currentPoolSize, desiredPoolSize, minDesiredPoolSize, maxDesiredPoolSize, stable, now, downsizeSummary, upsizeSummary});
        
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
    
    @Override
    public String toString() {
        return getClass().getSimpleName() + (truth(name) ? "("+name+")" : "");
    }
}
