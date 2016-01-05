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
package org.apache.brooklyn.policy.autoscaling;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.brooklyn.util.JavaGroovyEquivalents.groovyTruth;
import groovy.lang.Closure;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.brooklyn.api.catalog.Catalog;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntityLocal;
import org.apache.brooklyn.api.policy.PolicySpec;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.api.sensor.Sensor;
import org.apache.brooklyn.api.sensor.SensorEvent;
import org.apache.brooklyn.api.sensor.SensorEventListener;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.BasicConfigKey;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.trait.Resizable;
import org.apache.brooklyn.core.entity.trait.Startable;
import org.apache.brooklyn.core.mgmt.BrooklynTaskTags;
import org.apache.brooklyn.core.policy.AbstractPolicy;
import org.apache.brooklyn.core.sensor.BasicNotificationSensor;
import org.apache.brooklyn.policy.autoscaling.SizeHistory.WindowSummary;
import org.apache.brooklyn.policy.loadbalancing.LoadBalancingPolicy;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.core.flags.SetFromFlag;
import org.apache.brooklyn.util.core.flags.TypeCoercions;
import org.apache.brooklyn.util.core.task.Tasks;
import org.apache.brooklyn.util.time.Duration;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.reflect.TypeToken;
import com.google.common.util.concurrent.ThreadFactoryBuilder;


/**
 * Policy that is attached to a {@link Resizable} entity and dynamically adjusts its size in response to
 * emitted {@code POOL_COLD} and {@code POOL_HOT} events. Alternatively, the policy can be configured to
 * keep a given metric within a required range.
 * <p>
 * This policy does not itself determine whether the pool is hot or cold, but instead relies on these
 * events being emitted by the monitored entity itself, or by another policy that is attached to it; see, 
 * for example, {@link LoadBalancingPolicy}.)
 */
@SuppressWarnings({"rawtypes", "unchecked"})
@Catalog(name="Auto-scaler", description="Policy that is attached to a Resizable entity and dynamically "
        + "adjusts its size in response to either keep a metric within a given range, or in response to "
        + "POOL_COLD and POOL_HOT events")
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
        private int minPoolSize = 1;
        private int maxPoolSize = Integer.MAX_VALUE;
        private Integer resizeDownIterationIncrement;
        private Integer resizeDownIterationMax;
        private Integer resizeUpIterationIncrement;
        private Integer resizeUpIterationMax;
        private Duration minPeriodBetweenExecs;
        private Duration resizeUpStabilizationDelay;
        private Duration resizeDownStabilizationDelay;
        private ResizeOperator resizeOperator;
        private Function<Entity,Integer> currentSizeOperator;
        private BasicNotificationSensor<?> poolHotSensor;
        private BasicNotificationSensor<?> poolColdSensor;
        private BasicNotificationSensor<?> poolOkSensor;
        private BasicNotificationSensor<? super MaxPoolSizeReachedEvent> maxSizeReachedSensor;
        private Duration maxReachedNotificationDelay;
        
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
        
        public Builder resizeUpIterationIncrement(Integer val) {
            this.resizeUpIterationIncrement = val; return this;
        }
        public Builder resizeUpIterationMax(Integer val) {
            this.resizeUpIterationMax = val; return this;
        }
        public Builder resizeDownIterationIncrement(Integer val) {
            this.resizeUpIterationIncrement = val; return this;
        }
        public Builder resizeDownIterationMax(Integer val) {
            this.resizeUpIterationMax = val; return this;
        }

        public Builder minPeriodBetweenExecs(Duration val) {
            this.minPeriodBetweenExecs = val; return this;
        }
        public Builder resizeUpStabilizationDelay(Duration val) {
            this.resizeUpStabilizationDelay = val; return this;
        }
        public Builder resizeDownStabilizationDelay(Duration val) {
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
        public Builder maxReachedNotificationDelay(Duration val) {
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
                    .putIfNotNull("resizeUpIterationMax", resizeUpIterationMax)
                    .putIfNotNull("resizeUpIterationIncrement", resizeUpIterationIncrement)
                    .putIfNotNull("resizeDownIterationMax", resizeDownIterationMax)
                    .putIfNotNull("resizeDownIterationIncrement", resizeDownIterationIncrement)
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
    
    @SuppressWarnings("serial")
    @SetFromFlag("metric")
    public static final ConfigKey<AttributeSensor<? extends Number>> METRIC = BasicConfigKey.builder(new TypeToken<AttributeSensor<? extends Number>>() {})
            .name("autoscaler.metric")
            .build();

    @SetFromFlag("entityWithMetric")
    public static final ConfigKey<Entity> ENTITY_WITH_METRIC = BasicConfigKey.builder(Entity.class)
            .name("autoscaler.entityWithMetric")
            .description("The Entity with the metric that will be monitored")
            .build();
    
    @SetFromFlag("metricLowerBound")
    public static final ConfigKey<Number> METRIC_LOWER_BOUND = BasicConfigKey.builder(Number.class)
            .name("autoscaler.metricLowerBound")
            .description("The lower bound of the monitored metric. Below this the policy will resize down")
            .reconfigurable(true)
            .build();
    
    @SetFromFlag("metricUpperBound")
    public static final ConfigKey<Number> METRIC_UPPER_BOUND = BasicConfigKey.builder(Number.class)
            .name("autoscaler.metricUpperBound")
            .description("The upper bound of the monitored metric. Above this the policy will resize up")
            .reconfigurable(true)
            .build();
    
    @SetFromFlag("resizeUpIterationIncrement")
    public static final ConfigKey<Integer> RESIZE_UP_ITERATION_INCREMENT = BasicConfigKey.builder(Integer.class)
            .name("autoscaler.resizeUpIterationIncrement")
            .description("Batch size for resizing up; the size will be increased by a multiple of this value")
            .defaultValue(1)
            .reconfigurable(true)
            .build();
    @SetFromFlag("resizeUpIterationMax")
    public static final ConfigKey<Integer> RESIZE_UP_ITERATION_MAX = BasicConfigKey.builder(Integer.class)
            .name("autoscaler.resizeUpIterationMax")
            .defaultValue(Integer.MAX_VALUE)
            .description("Maximum change to the size on a single iteration when scaling up")
            .reconfigurable(true)
            .build();
    @SetFromFlag("resizeDownIterationIncrement")
    public static final ConfigKey<Integer> RESIZE_DOWN_ITERATION_INCREMENT = BasicConfigKey.builder(Integer.class)
            .name("autoscaler.resizeDownIterationIncrement")
            .description("Batch size for resizing down; the size will be decreased by a multiple of this value")
            .defaultValue(1)
            .reconfigurable(true)
            .build();
    @SetFromFlag("resizeDownIterationMax")
    public static final ConfigKey<Integer> RESIZE_DOWN_ITERATION_MAX = BasicConfigKey.builder(Integer.class)
            .name("autoscaler.resizeDownIterationMax")
            .defaultValue(Integer.MAX_VALUE)
            .description("Maximum change to the size on a single iteration when scaling down")
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
            .defaultValue(1)
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
    
    @SuppressWarnings("serial")
    @SetFromFlag("currentSizeOperator")
    public static final ConfigKey<Function<Entity,Integer>> CURRENT_SIZE_OPERATOR = BasicConfigKey.builder(new TypeToken<Function<Entity,Integer>>() {})
            .name("autoscaler.currentSizeOperator")
            .defaultValue(new Function<Entity,Integer>() {
                    public Integer apply(Entity entity) {
                        return ((Resizable)entity).getCurrentSize();
                    }})
            .build();

    @SuppressWarnings("serial")
    @SetFromFlag("poolHotSensor")
    public static final ConfigKey<BasicNotificationSensor<? extends Map>> POOL_HOT_SENSOR = BasicConfigKey.builder(new TypeToken<BasicNotificationSensor<? extends Map>>() {})
            .name("autoscaler.poolHotSensor")
            .defaultValue(DEFAULT_POOL_HOT_SENSOR)
            .build();

    @SuppressWarnings("serial")
    @SetFromFlag("poolColdSensor")
    public static final ConfigKey<BasicNotificationSensor<? extends Map>> POOL_COLD_SENSOR = BasicConfigKey.builder(new TypeToken<BasicNotificationSensor<? extends Map>>() {})
            .name("autoscaler.poolColdSensor")
            .defaultValue(DEFAULT_POOL_COLD_SENSOR)
            .build();

    @SuppressWarnings("serial")
    @SetFromFlag("poolOkSensor")
    public static final ConfigKey<BasicNotificationSensor<? extends Map>> POOL_OK_SENSOR = BasicConfigKey.builder(new TypeToken<BasicNotificationSensor<? extends Map>>() {})
            .name("autoscaler.poolOkSensor")
            .defaultValue(DEFAULT_POOL_OK_SENSOR)
            .build();

    @SuppressWarnings("serial")
    @SetFromFlag("maxSizeReachedSensor")
    public static final ConfigKey<BasicNotificationSensor<? super MaxPoolSizeReachedEvent>> MAX_SIZE_REACHED_SENSOR = BasicConfigKey.builder(new TypeToken<BasicNotificationSensor<? super MaxPoolSizeReachedEvent>>() {})
            .name("autoscaler.maxSizeReachedSensor")
            .description("Sensor for which a notification will be emitted (on the associated entity) when " +
                    "we consistently wanted to resize the pool above the max allowed size, for " +
                    "maxReachedNotificationDelay milliseconds")
            .build();
    
    @SetFromFlag("maxReachedNotificationDelay")
    public static final ConfigKey<Duration> MAX_REACHED_NOTIFICATION_DELAY = BasicConfigKey.builder(Duration.class)
            .name("autoscaler.maxReachedNotificationDelay")
            .description("Time that we consistently wanted to go above the maxPoolSize for, after which the " +
                    "maxSizeReachedSensor (if any) will be emitted")
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
        config().set(METRIC_LOWER_BOUND, checkNotNull(val));
    }
    
    public void setMetricUpperBound(Number val) {
        if (LOG.isInfoEnabled()) LOG.info("{} changing metricUpperBound from {} to {}", new Object[] {this, getMetricUpperBound(), val});
        config().set(METRIC_UPPER_BOUND, checkNotNull(val));
    }
    
    private <T> void setOrDefault(ConfigKey<T> key, T val) {
        if (val==null) val = key.getDefaultValue();
        config().set(key, val);
    }
    public int getResizeUpIterationIncrement() { return getConfig(RESIZE_UP_ITERATION_INCREMENT); }
    public void setResizeUpIterationIncrement(Integer val) {
        if (LOG.isInfoEnabled()) LOG.info("{} changing resizeUpIterationIncrement from {} to {}", new Object[] {this, getResizeUpIterationIncrement(), val});
        setOrDefault(RESIZE_UP_ITERATION_INCREMENT, val);
    }
    public int getResizeDownIterationIncrement() { return getConfig(RESIZE_DOWN_ITERATION_INCREMENT); }
    public void setResizeDownIterationIncrement(Integer val) {
        if (LOG.isInfoEnabled()) LOG.info("{} changing resizeDownIterationIncrement from {} to {}", new Object[] {this, getResizeDownIterationIncrement(), val});
        setOrDefault(RESIZE_DOWN_ITERATION_INCREMENT, val);
    }
    public int getResizeUpIterationMax() { return getConfig(RESIZE_UP_ITERATION_MAX); }
    public void setResizeUpIterationMax(Integer val) {
        if (LOG.isInfoEnabled()) LOG.info("{} changing resizeUpIterationMax from {} to {}", new Object[] {this, getResizeUpIterationMax(), val});
        setOrDefault(RESIZE_UP_ITERATION_MAX, val);
    }
    public int getResizeDownIterationMax() { return getConfig(RESIZE_DOWN_ITERATION_MAX); }
    public void setResizeDownIterationMax(Integer val) {
        if (LOG.isInfoEnabled()) LOG.info("{} changing resizeDownIterationMax from {} to {}", new Object[] {this, getResizeDownIterationMax(), val});
        setOrDefault(RESIZE_DOWN_ITERATION_MAX, val);
    }

    public void setMinPeriodBetweenExecs(Duration val) {
        if (LOG.isInfoEnabled()) LOG.info("{} changing minPeriodBetweenExecs from {} to {}", new Object[] {this, getMinPeriodBetweenExecs(), val});
        config().set(MIN_PERIOD_BETWEEN_EXECS, val);
    }

    public void setResizeUpStabilizationDelay(Duration val) {
        if (LOG.isInfoEnabled()) LOG.info("{} changing resizeUpStabilizationDelay from {} to {}", new Object[] {this, getResizeUpStabilizationDelay(), val});
        config().set(RESIZE_UP_STABILIZATION_DELAY, val);
    }
    
    public void setResizeDownStabilizationDelay(Duration val) {
        if (LOG.isInfoEnabled()) LOG.info("{} changing resizeDownStabilizationDelay from {} to {}", new Object[] {this, getResizeDownStabilizationDelay(), val});
        config().set(RESIZE_DOWN_STABILIZATION_DELAY, val);
    }
    
    public void setMinPoolSize(int val) {
        if (LOG.isInfoEnabled()) LOG.info("{} changing minPoolSize from {} to {}", new Object[] {this, getMinPoolSize(), val});
        config().set(MIN_POOL_SIZE, val);
    }
    
    public void setMaxPoolSize(int val) {
        if (LOG.isInfoEnabled()) LOG.info("{} changing maxPoolSize from {} to {}", new Object[] {this, getMaxPoolSize(), val});
        config().set(MAX_POOL_SIZE, val);
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
            // see above
        } else if (key.equals(RESIZE_UP_ITERATION_INCREMENT) || key.equals(RESIZE_UP_ITERATION_MAX) || key.equals(RESIZE_DOWN_ITERATION_INCREMENT) || key.equals(RESIZE_DOWN_ITERATION_MAX)) {
            // no special actions needed
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
        if (!config().getRaw(RESIZE_OPERATOR).isPresentAndNonNull()) {
            Preconditions.checkArgument(entity instanceof Resizable, "Provided entity "+entity+" must be an instance of Resizable, because no custom-resizer operator supplied");
        }
        super.setEntity(entity);
        this.poolEntity = entity;
        
        if (getMetric() != null) {
            Entity entityToSubscribeTo = (getEntityWithMetric() != null) ? getEntityWithMetric() : entity;
            subscriptions().subscribe(entityToSubscribeTo, getMetric(), metricEventHandler);
        }
        subscriptions().subscribe(poolEntity, getPoolColdSensor(), utilizationEventHandler);
        subscriptions().subscribe(poolEntity, getPoolHotSensor(), utilizationEventHandler);
        subscriptions().subscribe(poolEntity, getPoolOkSensor(), utilizationEventHandler);
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
    
    private enum ScalingType { HOT, COLD }
    private static class ScalingData {
        ScalingType scalingMode;
        int currentSize;
        double currentMetricValue;
        Double metricUpperBound;
        Double metricLowerBound;
        
        public double getCurrentTotalActivity() {
            return currentMetricValue * currentSize;
        }
        
        public boolean isHot() {
            return ((scalingMode==null || scalingMode==ScalingType.HOT) && isValid(metricUpperBound) && currentMetricValue > metricUpperBound);
        }
        public boolean isCold() {
            return ((scalingMode==null || scalingMode==ScalingType.COLD) && isValid(metricLowerBound) && currentMetricValue < metricLowerBound);
        }
        private boolean isValid(Double bound) {
            return (bound!=null && bound>0);
        }
    }

    private void onMetricChanged(Number val) {
        if (LOG.isTraceEnabled()) LOG.trace("{} recording pool-metric for {}: {}", new Object[] {this, poolEntity, val});

        if (val==null) {
            // occurs e.g. if using an aggregating enricher who returns null when empty, the sensor has gone away
            if (LOG.isTraceEnabled()) LOG.trace("{} not resizing pool {}, inbound metric is null", new Object[] {this, poolEntity});
            return;
        }
        
        ScalingData data = new ScalingData();
        data.currentMetricValue = val.doubleValue();
        data.currentSize = getCurrentSizeOperator().apply(entity);
        data.metricUpperBound = getMetricUpperBound().doubleValue();
        data.metricLowerBound = getMetricLowerBound().doubleValue();
        
        analyze(data, "pool");
    }
    
    private void onPoolCold(Map<String, ?> properties) {
        if (LOG.isTraceEnabled()) LOG.trace("{} recording pool-cold for {}: {}", new Object[] {this, poolEntity, properties});
        analyzeOnHotOrColdSensor(ScalingType.COLD, "cold pool", properties);
    }
    
    private void onPoolHot(Map<String, ?> properties) {
        if (LOG.isTraceEnabled()) LOG.trace("{} recording pool-hot for {}: {}", new Object[] {this, poolEntity, properties});
        analyzeOnHotOrColdSensor(ScalingType.HOT, "hot pool", properties);
    }
    
    private void analyzeOnHotOrColdSensor(ScalingType scalingMode, String description, Map<String, ?> properties) {
        ScalingData data = new ScalingData();
        data.scalingMode = scalingMode;
        data.currentMetricValue = (Double) properties.get(POOL_CURRENT_WORKRATE_KEY);
        data.currentSize = (Integer) properties.get(POOL_CURRENT_SIZE_KEY);
        data.metricUpperBound = (Double) properties.get(POOL_HIGH_THRESHOLD_KEY);
        data.metricLowerBound = (Double) properties.get(POOL_LOW_THRESHOLD_KEY);
        
        analyze(data, description);   
    }
    
    private void analyze(ScalingData data, String description) {
        int desiredSizeUnconstrained;
        
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
        if (data.isHot()) {
            // scale out
            desiredSizeUnconstrained = (int)Math.ceil(data.getCurrentTotalActivity() / data.metricUpperBound);
            data.scalingMode = ScalingType.HOT;
            
        } else if (data.isCold()) {
            // scale back
            desiredSizeUnconstrained = (int)Math.floor(data.getCurrentTotalActivity() / data.metricLowerBound);
            data.scalingMode = ScalingType.COLD;
            
        } else {
            if (LOG.isTraceEnabled()) LOG.trace("{} not resizing pool {} from {} ({} within range {}..{})", new Object[] {this, poolEntity, data.currentSize, data.currentMetricValue, data.metricLowerBound, data.metricUpperBound});
            abortResize(data.currentSize);
            return; // within the healthy range; no-op
        }
        
        if (LOG.isTraceEnabled()) LOG.debug("{} detected unconstrained desired size {}", new Object[] {this, desiredSizeUnconstrained});
        int desiredSize = applyMinMaxConstraints(desiredSizeUnconstrained);

        if ((data.scalingMode==ScalingType.COLD) && (desiredSize < data.currentSize)) {

            int delta = data.currentSize - desiredSize;
            int scaleIncrement = getResizeDownIterationIncrement();
            int scaleMax = getResizeDownIterationMax();
            if (delta>scaleMax) {
                delta=scaleMax;
            } else if (delta % scaleIncrement != 0) {
                // keep scaling to the increment
                delta += scaleIncrement - (delta % scaleIncrement);
            }
            desiredSize = data.currentSize - delta;
            
            if (data.metricUpperBound!=null) {
                // if upper bound supplied, check that this desired scale-back size 
                // is not going to cause scale-out on next run; i.e. anti-thrashing
                while (desiredSize < data.currentSize && data.getCurrentTotalActivity() > data.metricUpperBound * desiredSize) {
                    if (LOG.isTraceEnabled()) LOG.trace("{} when resizing back pool {} from {}, tweaking from {} to prevent thrashing", new Object[] {this, poolEntity, data.currentSize, desiredSize });
                    desiredSize += scaleIncrement;
                }
            }
            desiredSize = applyMinMaxConstraints(desiredSize);
            if (desiredSize >= data.currentSize) data.scalingMode = null;
            
        } else if ((data.scalingMode==ScalingType.HOT) && (desiredSize > data.currentSize)) {

            int delta = desiredSize - data.currentSize;
            int scaleIncrement = getResizeUpIterationIncrement();
            int scaleMax = getResizeUpIterationMax();
            if (delta>scaleMax) {
                delta=scaleMax;
            } else if (delta % scaleIncrement != 0) {
                // keep scaling to the increment
                delta += scaleIncrement - (delta % scaleIncrement);
            }
            desiredSize = data.currentSize + delta;
            desiredSize = applyMinMaxConstraints(desiredSize);
            if (desiredSize <= data.currentSize) data.scalingMode = null;

        } else {
            data.scalingMode = null;
        }
    
        if (data.scalingMode!=null) {
            if (LOG.isDebugEnabled()) LOG.debug("{} provisionally resizing {} {} from {} to {} ({} < {}; ideal size {})", new Object[] {this, description, poolEntity, data.currentSize, desiredSize, data.currentMetricValue, data.metricLowerBound, desiredSizeUnconstrained});
            scheduleResize(desiredSize);
        } else {
            if (LOG.isTraceEnabled()) LOG.trace("{} not resizing {} {} from {} to {}, {} out of healthy range {}..{} but unconstrained size {} blocked by bounds/check", new Object[] {this, description, poolEntity, data.currentSize, desiredSize, data.currentMetricValue, data.metricLowerBound, data.metricUpperBound, desiredSizeUnconstrained});
            abortResize(data.currentSize);
            // but add to the unbounded record for future consideration
        }
        
        onNewUnboundedPoolSize(desiredSizeUnconstrained);
    }

    private int applyMinMaxConstraints(int desiredSize) {
        desiredSize = Math.max(getMinPoolSize(), desiredSize);
        desiredSize = Math.min(getMaxPoolSize(), desiredSize);
        return desiredSize;
    }

    private void onPoolOk(Map<String, ?> properties) {
        if (LOG.isTraceEnabled()) LOG.trace("{} recording pool-ok for {}: {}", new Object[] {this, poolEntity, properties});
        
        int poolCurrentSize = (Integer) properties.get(POOL_CURRENT_SIZE_KEY);
        
        if (LOG.isTraceEnabled()) LOG.trace("{} not resizing ok pool {} from {}", new Object[] {this, poolEntity, poolCurrentSize});
        abortResize(poolCurrentSize);
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
     * Piggy-backs off the existing scheduleResize execution, which now also checks if the listener
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
            entity.sensors().emit(maxSizeReachedSensor, event);
            
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
        final long desiredPoolSize = calculatedDesiredPoolSize.size;
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
        
        Entities.submit(entity, Tasks.<Void>builder().displayName("Auto-scaler")
            .description("Auto-scaler recommending resize from "+currentPoolSize+" to "+desiredPoolSize)
            .tag(BrooklynTaskTags.NON_TRANSIENT_TASK_TAG)
            .body(new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    // TODO Should we use int throughout, rather than casting here?
                    getResizeOperator().resize(poolEntity, (int) desiredPoolSize);
                    return null;
                }
            }).build())
            .blockUntilEnded();
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
        return getClass().getSimpleName() + (groovyTruth(name) ? "("+name+")" : "");
    }
}
