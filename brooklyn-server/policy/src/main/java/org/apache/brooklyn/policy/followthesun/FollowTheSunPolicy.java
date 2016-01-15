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
package org.apache.brooklyn.policy.followthesun;

import static com.google.common.base.Preconditions.checkArgument;
import static org.apache.brooklyn.util.JavaGroovyEquivalents.elvis;
import static org.apache.brooklyn.util.JavaGroovyEquivalents.groovyTruth;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntityLocal;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.location.MachineProvisioningLocation;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.api.sensor.Sensor;
import org.apache.brooklyn.api.sensor.SensorEvent;
import org.apache.brooklyn.api.sensor.SensorEventListener;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.policy.AbstractPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.brooklyn.policy.followthesun.FollowTheSunPool.ContainerItemPair;
import org.apache.brooklyn.policy.loadbalancing.Movable;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.core.flags.SetFromFlag;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

    // removed from catalog because it cannot currently be configured via catalog mechanisms - 
    // PolicySpec.create fails due to no no-arg constructor
    // TODO make model and parameters things which can be initialized from config then reinstate in catalog
//@Catalog(name="Follow the Sun", description="Policy for moving \"work\" around to follow the demand; "
//        + "the work can be any \"Movable\" entity")
public class FollowTheSunPolicy extends AbstractPolicy {

    private static final Logger LOG = LoggerFactory.getLogger(FollowTheSunPolicy.class);

    public static final String NAME = "Follow the Sun (Inter-Geography Latency Optimization)";

    @SetFromFlag(defaultVal="100")
    private long minPeriodBetweenExecs;
    
    @SetFromFlag
    private Function<Entity, Location> locationFinder;
    
    private final AttributeSensor<Map<? extends Movable, Double>> itemUsageMetric;
    private final FollowTheSunModel<Entity, Movable> model;
    private final FollowTheSunStrategy<Entity, Movable> strategy;
    private final FollowTheSunParameters parameters;
    
    private FollowTheSunPool poolEntity;
    
    private volatile ScheduledExecutorService executor;
    private final AtomicBoolean executorQueued = new AtomicBoolean(false);
    private volatile long executorTime = 0;
    private boolean loggedConstraintsIgnored = false;
    
    private final Function<Entity, Location> defaultLocationFinder = new Function<Entity, Location>() {
        public Location apply(Entity e) {
            Collection<Location> locs = e.getLocations();
            if (locs.isEmpty()) return null;
            Location contender = Iterables.get(locs, 0);
            while (contender.getParent() != null && !(contender instanceof MachineProvisioningLocation)) {
                contender = contender.getParent();
            }
            return contender;
        }
    };
    
    private final SensorEventListener<Object> eventHandler = new SensorEventListener<Object>() {
        @Override
        public void onEvent(SensorEvent<Object> event) {
            if (LOG.isTraceEnabled()) LOG.trace("{} received event {}", FollowTheSunPolicy.this, event);
            Entity source = event.getSource();
            Object value = event.getValue();
            Sensor<?> sensor = event.getSensor();
            
            if (sensor.equals(itemUsageMetric)) {
                onItemMetricUpdated((Movable)source, (Map<? extends Movable, Double>) value, true);
            } else if (sensor.equals(Attributes.LOCATION_CHANGED)) {
                onContainerLocationUpdated(source, true);
            } else if (sensor.equals(FollowTheSunPool.CONTAINER_ADDED)) {
                onContainerAdded((Entity) value, true);
            } else if (sensor.equals(FollowTheSunPool.CONTAINER_REMOVED)) {
                onContainerRemoved((Entity) value, true);
            } else if (sensor.equals(FollowTheSunPool.ITEM_ADDED)) {
                onItemAdded((Movable) value, true);
            } else if (sensor.equals(FollowTheSunPool.ITEM_REMOVED)) {
                onItemRemoved((Movable) value, true);
            } else if (sensor.equals(FollowTheSunPool.ITEM_MOVED)) {
                ContainerItemPair pair = (ContainerItemPair) value;
                onItemMoved((Movable)pair.item, pair.container, true);
            }
        }
    };
    
    // FIXME parameters: use a more groovy way of doing it, that's consistent with other policies/entities?
    public FollowTheSunPolicy(AttributeSensor itemUsageMetric, 
            FollowTheSunModel<Entity, Movable> model, FollowTheSunParameters parameters) {
        this(MutableMap.of(), itemUsageMetric, model, parameters);
    }
    
    public FollowTheSunPolicy(Map props, AttributeSensor itemUsageMetric, 
            FollowTheSunModel<Entity, Movable> model, FollowTheSunParameters parameters) {
        super(props);
        this.itemUsageMetric = itemUsageMetric;
        this.model = model;
        this.parameters = parameters;
        this.strategy = new FollowTheSunStrategy<Entity, Movable>(model, parameters); // TODO: extract interface, inject impl
        this.locationFinder = elvis(locationFinder, defaultLocationFinder);
        
        // TODO Should re-use the execution manager's thread pool, somehow
        executor = Executors.newSingleThreadScheduledExecutor(newThreadFactory());
    }
    
    @Override
    public void setEntity(EntityLocal entity) {
        checkArgument(entity instanceof FollowTheSunPool, "Provided entity must be a FollowTheSunPool");
        super.setEntity(entity);
        this.poolEntity = (FollowTheSunPool) entity;
        
        // Detect when containers are added to or removed from the pool.
        subscriptions().subscribe(poolEntity, FollowTheSunPool.CONTAINER_ADDED, eventHandler);
        subscriptions().subscribe(poolEntity, FollowTheSunPool.CONTAINER_REMOVED, eventHandler);
        subscriptions().subscribe(poolEntity, FollowTheSunPool.ITEM_ADDED, eventHandler);
        subscriptions().subscribe(poolEntity, FollowTheSunPool.ITEM_REMOVED, eventHandler);
        subscriptions().subscribe(poolEntity, FollowTheSunPool.ITEM_MOVED, eventHandler);
        
        // Take heed of any extant containers.
        for (Entity container : poolEntity.getContainerGroup().getMembers()) {
            onContainerAdded(container, false);
        }
        for (Entity item : poolEntity.getItemGroup().getMembers()) {
            onItemAdded((Movable)item, false);
        }

        scheduleLatencyReductionJig();
    }
    
    @Override
    public void suspend() {
        // TODO unsubscribe from everything? And resubscribe on resume?
        super.suspend();
        if (executor != null) executor.shutdownNow();
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

    private void scheduleLatencyReductionJig() {
        if (isRunning() && executorQueued.compareAndSet(false, true)) {
            long now = System.currentTimeMillis();
            long delay = Math.max(0, (executorTime + minPeriodBetweenExecs) - now);
            
            executor.schedule(new Runnable() {
                public void run() {
                    try {
                        executorTime = System.currentTimeMillis();
                        executorQueued.set(false);
                        
                        if (LOG.isTraceEnabled()) LOG.trace("{} executing follow-the-sun migration-strategy", this);
                        strategy.rebalance();
                        
                    } catch (RuntimeException e) {
                        if (isRunning()) {
                            LOG.error("Error during latency-reduction-jig", e);
                        } else {
                            LOG.debug("Error during latency-reduction-jig, but no longer running", e);
                        }
                    }
                }},
                delay,
                TimeUnit.MILLISECONDS);
        }
    }
    
    private void onContainerAdded(Entity container, boolean rebalanceNow) {
        subscriptions().subscribe(container, Attributes.LOCATION_CHANGED, eventHandler);
        Location location = locationFinder.apply(container);
        
        if (LOG.isTraceEnabled()) LOG.trace("{} recording addition of container {} in location {}", new Object[] {this, container, location});
        model.onContainerAdded(container, location);
        
        if (rebalanceNow) scheduleLatencyReductionJig();
    }
    
    private void onContainerRemoved(Entity container, boolean rebalanceNow) {
        if (LOG.isTraceEnabled()) LOG.trace("{} recording removal of container {}", this, container);
        model.onContainerRemoved(container);
        if (rebalanceNow) scheduleLatencyReductionJig();
    }
    
    private void onItemAdded(Movable item, boolean rebalanceNow) {
        Entity parentContainer = (Entity) item.getAttribute(Movable.CONTAINER);
        
        if (LOG.isTraceEnabled()) LOG.trace("{} recording addition of item {} in container {}", new Object[] {this, item, parentContainer});
        
        subscriptions().subscribe(item, itemUsageMetric, eventHandler);
        
        // Update the model, including the current metric value (if any).
        Map<? extends Movable, Double> currentValue = item.getAttribute(itemUsageMetric);
        boolean immovable = (Boolean)elvis(item.getConfig(Movable.IMMOVABLE), false);
        model.onItemAdded(item, parentContainer, immovable);

        if (currentValue != null) {
            model.onItemUsageUpdated(item, currentValue);
        }
        
        if (rebalanceNow) scheduleLatencyReductionJig();
    }
    
    private void onItemRemoved(Movable item, boolean rebalanceNow) {
        if (LOG.isTraceEnabled()) LOG.trace("{} recording removal of item {}", this, item);
        subscriptions().unsubscribe(item);
        model.onItemRemoved(item);
        if (rebalanceNow) scheduleLatencyReductionJig();
    }
    
    private void onItemMoved(Movable item, Entity parentContainer, boolean rebalanceNow) {
        if (LOG.isTraceEnabled()) LOG.trace("{} recording moving of item {} to {}", new Object[] {this, item, parentContainer});
        model.onItemMoved(item, parentContainer);
        if (rebalanceNow) scheduleLatencyReductionJig();
    }
    
    private void onContainerLocationUpdated(Entity container, boolean rebalanceNow) {
        Location location = locationFinder.apply(container);
        if (LOG.isTraceEnabled()) LOG.trace("{} recording location for container {}, new value {}", new Object[] {this, container, location});
        model.onContainerLocationUpdated(container, location);
        if (rebalanceNow) scheduleLatencyReductionJig();
    }
    
    private void onItemMetricUpdated(Movable item, Map<? extends Movable, Double> newValues, boolean rebalanceNow) {
        if (LOG.isTraceEnabled()) LOG.trace("{} recording usage update for item {}, new value {}", new Object[] {this, item, newValues});
        model.onItemUsageUpdated(item, newValues);
        if (rebalanceNow) scheduleLatencyReductionJig();
    }
    
    @Override
    public String toString() {
        return getClass().getSimpleName() + (groovyTruth(name) ? "("+name+")" : "");
    }
}
