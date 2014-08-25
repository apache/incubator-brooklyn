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
package brooklyn.policy.loadbalancing;

import static brooklyn.util.JavaGroovyEquivalents.elvis;
import static brooklyn.util.JavaGroovyEquivalents.groovyTruth;

import java.util.Map;
import java.util.Map.Entry;
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
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.event.AttributeSensor;
import brooklyn.event.Sensor;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.policy.autoscaling.AutoScalerPolicy;
import brooklyn.policy.basic.AbstractPolicy;
import brooklyn.policy.loadbalancing.BalanceableWorkerPool.ContainerItemPair;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.flags.SetFromFlag;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ThreadFactoryBuilder;


/**
 * <p>Policy that is attached to a pool of "containers", each of which can host one or more migratable "items".
 * The policy monitors the workrates of the items and effects migrations in an attempt to ensure that the containers
 * are all sufficiently utilized without any of them being overloaded.
 * 
 * <p>The particular sensor that defines the items' workrates is specified when the policy is constructed. High- and
 * low-thresholds are defined as <strong>configuration keys</strong> on each of the container entities in the pool:
 * for an item sensor named {@code foo.bar.sensorName}, the corresponding container config keys would be named
 * {@code foo.bar.sensorName.threshold.low} and {@code foo.bar.sensorName.threshold.high}.
 * 
 * <p>In addition to balancing items among the available containers, this policy causes the pool Entity to emit
 * {@code POOL_COLD} and {@code POOL_HOT} events when it is determined that there is a surplus or shortfall
 * of container resource in the pool respectively. These events may be consumed by a separate policy that is capable
 * of resizing the container pool.
 */
@Catalog
public class LoadBalancingPolicy<NodeType extends Entity, ItemType extends Movable> extends AbstractPolicy {
    
    private static final Logger LOG = LoggerFactory.getLogger(LoadBalancingPolicy.class);
    
    @SetFromFlag(defaultVal="100")
    private long minPeriodBetweenExecs;
    
    private final AttributeSensor<? extends Number> metric;
    private final String lowThresholdConfigKeyName;
    private final String highThresholdConfigKeyName;
    private final BalanceablePoolModel<NodeType, ItemType> model;
    private final BalancingStrategy<NodeType, ItemType> strategy;
    private BalanceableWorkerPool poolEntity;
    
    private volatile ScheduledExecutorService executor;
    private final AtomicBoolean executorQueued = new AtomicBoolean(false);
    private volatile long executorTime = 0;

    private int lastEmittedDesiredPoolSize = 0;
    private static enum TemperatureStates { COLD, HOT }
    private TemperatureStates lastEmittedPoolTemperature = null; // "cold" or "hot"
    
    private final SensorEventListener<Object> eventHandler = new SensorEventListener<Object>() {
        @SuppressWarnings({ "rawtypes", "unchecked" })
        public void onEvent(SensorEvent<Object> event) {
            if (LOG.isTraceEnabled()) LOG.trace("{} received event {}", LoadBalancingPolicy.this, event);
            Entity source = event.getSource();
            Object value = event.getValue();
            Sensor sensor = event.getSensor();
            
            if (sensor.equals(metric)) {
                onItemMetricUpdate((ItemType)source, ((Number) value).doubleValue(), true);
            } else if (sensor.equals(BalanceableWorkerPool.CONTAINER_ADDED)) {
                onContainerAdded((NodeType) value, true);
            } else if (sensor.equals(BalanceableWorkerPool.CONTAINER_REMOVED)) {
                onContainerRemoved((NodeType) value, true);
            } else if (sensor.equals(BalanceableWorkerPool.ITEM_ADDED)) {
                ContainerItemPair pair = (ContainerItemPair) value;
                onItemAdded((ItemType)pair.item, (NodeType)pair.container, true);
            } else if (sensor.equals(BalanceableWorkerPool.ITEM_REMOVED)) {
                ContainerItemPair pair = (ContainerItemPair) value;
                onItemRemoved((ItemType)pair.item, (NodeType)pair.container, true);
            } else if (sensor.equals(BalanceableWorkerPool.ITEM_MOVED)) {
                ContainerItemPair pair = (ContainerItemPair) value;
                onItemMoved((ItemType)pair.item, (NodeType)pair.container, true);
            }
        }
    };

    public LoadBalancingPolicy(AttributeSensor<? extends Number> metric,
            BalanceablePoolModel<NodeType, ItemType> model) {
        this(MutableMap.of(), metric, model);
    }
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public LoadBalancingPolicy(Map props, AttributeSensor<? extends Number> metric,
            BalanceablePoolModel<NodeType, ItemType> model) {
        
        super(props);
        this.metric = metric;
        this.lowThresholdConfigKeyName = metric.getName()+".threshold.low";
        this.highThresholdConfigKeyName = metric.getName()+".threshold.high";
        this.model = model;
        this.strategy = new BalancingStrategy(getDisplayName(), model); // TODO: extract interface, inject impl
        
        // TODO Should re-use the execution manager's thread pool, somehow
        executor = Executors.newSingleThreadScheduledExecutor(newThreadFactory());
    }
    
    @SuppressWarnings("unchecked")
    @Override
    public void setEntity(EntityLocal entity) {
        Preconditions.checkArgument(entity instanceof BalanceableWorkerPool, "Provided entity must be a BalanceableWorkerPool");
        super.setEntity(entity);
        this.poolEntity = (BalanceableWorkerPool) entity;
        
        // Detect when containers are added to or removed from the pool.
        subscribe(poolEntity, BalanceableWorkerPool.CONTAINER_ADDED, eventHandler);
        subscribe(poolEntity, BalanceableWorkerPool.CONTAINER_REMOVED, eventHandler);
        subscribe(poolEntity, BalanceableWorkerPool.ITEM_ADDED, eventHandler);
        subscribe(poolEntity, BalanceableWorkerPool.ITEM_REMOVED, eventHandler);
        subscribe(poolEntity, BalanceableWorkerPool.ITEM_MOVED, eventHandler);
        
        // Take heed of any extant containers.
        for (Entity container : poolEntity.getContainerGroup().getMembers()) {
            onContainerAdded((NodeType)container, false);
        }
        for (Entity item : poolEntity.getItemGroup().getMembers()) {
            onItemAdded((ItemType)item, (NodeType)item.getAttribute(Movable.CONTAINER), false);
        }

        scheduleRebalance();
    }
    
    @Override
    public void suspend() {
        // TODO unsubscribe from everything? And resubscribe on resume?
        super.suspend();
        if (executor != null) executor.shutdownNow();;
        executorQueued.set(false);
    }
    
    @Override
    public void resume() {
        super.resume();
        executor = Executors.newSingleThreadScheduledExecutor(newThreadFactory());
        executorTime = 0;
        executorQueued.set(false);
    }
    
    private ThreadFactory newThreadFactory() {
        return new ThreadFactoryBuilder()
                .setNameFormat("brooklyn-followthesunpolicy-%d")
                .build();
    }

    private void scheduleRebalance() {
        if (isRunning() && executorQueued.compareAndSet(false, true)) {
            long now = System.currentTimeMillis();
            long delay = Math.max(0, (executorTime + minPeriodBetweenExecs) - now);
            
            executor.schedule(new Runnable() {
                @SuppressWarnings("rawtypes")
                public void run() {
                    try {
                        executorTime = System.currentTimeMillis();
                        executorQueued.set(false);
                        strategy.rebalance();

                        if (LOG.isDebugEnabled()) LOG.debug("{} post-rebalance: poolSize={}; workrate={}; lowThreshold={}; " + 
                                "highThreshold={}", new Object[] {this, model.getPoolSize(), model.getCurrentPoolWorkrate(), 
                                model.getPoolLowThreshold(), model.getPoolHighThreshold()});
                        
                        if (model.isCold()) {
                            Map eventVal = ImmutableMap.of(
                                    AutoScalerPolicy.POOL_CURRENT_SIZE_KEY, model.getPoolSize(),
                                    AutoScalerPolicy.POOL_CURRENT_WORKRATE_KEY, model.getCurrentPoolWorkrate(),
                                    AutoScalerPolicy.POOL_LOW_THRESHOLD_KEY, model.getPoolLowThreshold(),
                                    AutoScalerPolicy.POOL_HIGH_THRESHOLD_KEY, model.getPoolHighThreshold());
            
                            ((EntityLocal)poolEntity).emit(AutoScalerPolicy.DEFAULT_POOL_COLD_SENSOR, eventVal);
                            
                            if (LOG.isInfoEnabled()) {
                                int desiredPoolSize = (int) Math.ceil(model.getCurrentPoolWorkrate() / (model.getPoolLowThreshold()/model.getPoolSize()));
                                if (desiredPoolSize != lastEmittedDesiredPoolSize || lastEmittedPoolTemperature != TemperatureStates.COLD) {
                                    LOG.info("{} emitted COLD (suggesting {}): {}", new Object[] {this, desiredPoolSize, eventVal});
                                    lastEmittedDesiredPoolSize = desiredPoolSize;
                                    lastEmittedPoolTemperature = TemperatureStates.COLD;
                                }
                            }
                        
                        } else if (model.isHot()) {
                            Map eventVal = ImmutableMap.of(
                                    AutoScalerPolicy.POOL_CURRENT_SIZE_KEY, model.getPoolSize(),
                                    AutoScalerPolicy.POOL_CURRENT_WORKRATE_KEY, model.getCurrentPoolWorkrate(),
                                    AutoScalerPolicy.POOL_LOW_THRESHOLD_KEY, model.getPoolLowThreshold(),
                                    AutoScalerPolicy.POOL_HIGH_THRESHOLD_KEY, model.getPoolHighThreshold());
                            
                            ((EntityLocal)poolEntity).emit(AutoScalerPolicy.DEFAULT_POOL_HOT_SENSOR, eventVal);
                            
                            if (LOG.isInfoEnabled()) {
                                int desiredPoolSize = (int) Math.ceil(model.getCurrentPoolWorkrate() / (model.getPoolHighThreshold()/model.getPoolSize()));
                                if (desiredPoolSize != lastEmittedDesiredPoolSize || lastEmittedPoolTemperature != TemperatureStates.HOT) {
                                    LOG.info("{} emitted HOT (suggesting {}): {}", new Object[] {this, desiredPoolSize, eventVal});
                                    lastEmittedDesiredPoolSize = desiredPoolSize;
                                    lastEmittedPoolTemperature = TemperatureStates.HOT;
                                }
                            }
                        }

                    } catch (Exception e) {
                        if (isRunning()) {
                            LOG.error("Error rebalancing", e);
                        } else {
                            LOG.debug("Error rebalancing, but no longer running", e);
                        }
                    }
                }},
                delay,
                TimeUnit.MILLISECONDS);
        }
    }
    
    // TODO Can get duplicate onContainerAdded events.
    //      I presume it's because we subscribe and then iterate over the extant containers.
    //      Solution would be for subscription to give you events for existing / current value(s).
    //      Also current impl messes up single-threaded updates model: the setEntity is a different thread than for subscription events.
    private void onContainerAdded(NodeType newContainer, boolean rebalanceNow) {
        Preconditions.checkArgument(newContainer instanceof BalanceableContainer, "Added container must be a BalanceableContainer");
        if (LOG.isTraceEnabled()) LOG.trace("{} recording addition of container {}", this, newContainer);
        // Low and high thresholds for the metric we're interested in are assumed to be present
        // in the container's configuration.
        Number lowThreshold = (Number) findConfigValue(newContainer, lowThresholdConfigKeyName);
        Number highThreshold = (Number) findConfigValue(newContainer, highThresholdConfigKeyName);
        if (lowThreshold == null || highThreshold == null) {
            LOG.warn(
                "Balanceable container '"+newContainer+"' does not define low- and high- threshold configuration keys: '"+
                lowThresholdConfigKeyName+"' and '"+highThresholdConfigKeyName+"', skipping");
            return;
        }
        
        model.onContainerAdded(newContainer, lowThreshold.doubleValue(), highThreshold.doubleValue());
        
        // Note: no need to scan the container for items; they will appear via the ITEM_ADDED events.
        // Also, must abide by any item-filters etc defined in the pool.
        
        if (rebalanceNow) scheduleRebalance();
    }
    
    private static Object findConfigValue(Entity entity, String configKeyName) {
        Map<ConfigKey<?>, Object> config = ((EntityInternal)entity).getAllConfig();
        for (Entry<ConfigKey<?>, Object> entry : config.entrySet()) {
            if (configKeyName.equals(entry.getKey().getName()))
                return entry.getValue();
        }
        return null;
    }
    
    // TODO Receiving duplicates of onContainerRemoved (e.g. when running LoadBalancingInmemorySoakTest)
    private void onContainerRemoved(NodeType oldContainer, boolean rebalanceNow) {
        if (LOG.isTraceEnabled()) LOG.trace("{} recording removal of container {}", this, oldContainer);
        model.onContainerRemoved(oldContainer);
        if (rebalanceNow) scheduleRebalance();
    }
    
    private void onItemAdded(ItemType item, NodeType parentContainer, boolean rebalanceNow) {
        Preconditions.checkArgument(item instanceof Movable, "Added item "+item+" must implement Movable");
        if (LOG.isTraceEnabled()) LOG.trace("{} recording addition of item {} in container {}", new Object[] {this, item, parentContainer});
        
        subscribe(item, metric, eventHandler);
        
        // Update the model, including the current metric value (if any).
        boolean immovable = (Boolean)elvis(item.getConfig(Movable.IMMOVABLE), false);
        Number currentValue = item.getAttribute(metric);
        model.onItemAdded(item, parentContainer, immovable);
        if (currentValue != null)
            model.onItemWorkrateUpdated(item, currentValue.doubleValue());
        
        if (rebalanceNow) scheduleRebalance();
    }
    
    private void onItemRemoved(ItemType item, NodeType parentContainer, boolean rebalanceNow) {
        if (LOG.isTraceEnabled()) LOG.trace("{} recording removal of item {}", this, item);
        unsubscribe(item);
        model.onItemRemoved(item);
        if (rebalanceNow) scheduleRebalance();
    }
    
    private void onItemMoved(ItemType item, NodeType parentContainer, boolean rebalanceNow) {
        if (LOG.isTraceEnabled()) LOG.trace("{} recording moving of item {} to {}", new Object[] {this, item, parentContainer});
        model.onItemMoved(item, parentContainer);
        if (rebalanceNow) scheduleRebalance();
    }
    
    private void onItemMetricUpdate(ItemType item, double newValue, boolean rebalanceNow) {
        if (LOG.isTraceEnabled()) LOG.trace("{} recording metric update for item {}, new value {}", new Object[] {this, item, newValue});
        model.onItemWorkrateUpdated(item, newValue);
        if (rebalanceNow) scheduleRebalance();
    }
    
    @Override
    public String toString() {
        return getClass().getSimpleName() + (groovyTruth(name) ? "("+name+")" : "");
    }
}
