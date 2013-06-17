package brooklyn.policy.followthesun;

import static brooklyn.util.GroovyJavaMethods.elvis;
import static brooklyn.util.GroovyJavaMethods.truth;
import static com.google.common.base.Preconditions.checkArgument;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.event.AttributeSensor;
import brooklyn.event.Sensor;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.location.Location;
import brooklyn.location.MachineProvisioningLocation;
import brooklyn.policy.basic.AbstractPolicy;
import brooklyn.policy.followthesun.FollowTheSunPool.ContainerItemPair;
import brooklyn.policy.loadbalancing.Movable;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.flags.SetFromFlag;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

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
        subscribe(poolEntity, FollowTheSunPool.CONTAINER_ADDED, eventHandler);
        subscribe(poolEntity, FollowTheSunPool.CONTAINER_REMOVED, eventHandler);
        subscribe(poolEntity, FollowTheSunPool.ITEM_ADDED, eventHandler);
        subscribe(poolEntity, FollowTheSunPool.ITEM_REMOVED, eventHandler);
        subscribe(poolEntity, FollowTheSunPool.ITEM_MOVED, eventHandler);
        
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
        subscribe(container, Attributes.LOCATION_CHANGED, eventHandler);
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
        
        subscribe(item, itemUsageMetric, eventHandler);
        
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
        unsubscribe(item);
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
        return getClass().getSimpleName() + (truth(name) ? "("+name+")" : "");
    }
}
