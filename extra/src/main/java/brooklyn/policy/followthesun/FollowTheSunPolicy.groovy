package brooklyn.policy.followthesun;

import static com.google.common.base.Preconditions.checkArgument

import java.util.Map
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Entity
import brooklyn.entity.basic.Attributes
import brooklyn.entity.basic.EntityLocal
import brooklyn.event.AttributeSensor
import brooklyn.event.Sensor
import brooklyn.event.SensorEvent
import brooklyn.event.SensorEventListener
import brooklyn.location.Location
import brooklyn.location.MachineProvisioningLocation
import brooklyn.policy.basic.AbstractPolicy
import brooklyn.policy.followthesun.FollowTheSunPool.ContainerItemPair
import brooklyn.policy.loadbalancing.Movable

import com.google.common.collect.Iterables

public class FollowTheSunPolicy extends AbstractPolicy {

    private static final Logger LOG = LoggerFactory.getLogger(FollowTheSunPolicy.class)

    public static final String NAME = "Follow the Sun (Inter-Geography Latency Optimization)";

    private final AttributeSensor<? extends Number> itemUsageMetric
    private final FollowTheSunModel<Entity, Entity> model
    private final FollowTheSunStrategy<Entity, ?> strategy
    private final FollowTheSunParameters parameters;
    private final Closure locationFinder
    
    private FollowTheSunPool poolEntity
    private ExecutorService executor = Executors.newSingleThreadExecutor()
    private AtomicBoolean executorQueued = new AtomicBoolean(false)

    private boolean loggedConstraintsIgnored = false;
    
    Closure defaultLocationFinder = { Entity e ->
        Collection<Location> locs = e.getLocations()
        if (locs.isEmpty()) return null
        Location contender = Iterables.get(locs, 0)
        while (contender.getParentLocation() != null && !(contender instanceof MachineProvisioningLocation)) {
            contender = contender.getParentLocation()
        }
        return contender
    }
    
    private final SensorEventListener<?> eventHandler = new SensorEventListener<Object>() {
        public void onEvent(SensorEvent<?> event) {
            if (LOG.isTraceEnabled()) LOG.trace("{} received event {}", FollowTheSunPolicy.this, event)
            Entity source = event.getSource()
            Object value = event.getValue()
            Sensor sensor = event.getSensor()
            switch (sensor) {
                case itemUsageMetric:
                    onItemMetricUpdated(source, (Map<? extends Entity, Double>) value, true)
                    break
                case Attributes.LOCATION_CHANGED:
                    onContainerLocationUpdated(source, true)
                    break
                case FollowTheSunPool.CONTAINER_ADDED:
                    onContainerAdded((Entity) value, true)
                    break
                case FollowTheSunPool.CONTAINER_REMOVED:
                    onContainerRemoved((Entity) value, true)
                    break
                case FollowTheSunPool.ITEM_ADDED:
                    onItemAdded((Entity) value, true)
                    break
                case FollowTheSunPool.ITEM_REMOVED:
                    onItemRemoved((Entity) value, true)
                    break
                case FollowTheSunPool.ITEM_MOVED:
                    ContainerItemPair pair = value
                    onItemMoved(pair.item, pair.container, true)
                    break
            }
        }
    }
    
    // FIXME parameters: use a more groovy way of doing it, that's consistent with other policies/entities?
    public FollowTheSunPolicy(Map flags = [:], AttributeSensor itemUsageMetric, 
            FollowTheSunModel<? extends Entity, ? extends Entity> model, FollowTheSunParameters parameters) {
        super(flags)
        this.itemUsageMetric = itemUsageMetric
        this.model = model
        this.parameters = parameters
        this.strategy = new FollowTheSunStrategy<Entity, Object>(model, parameters) // TODO: extract interface, inject impl
        this.locationFinder = flags.locationFinder ?: defaultLocationFinder
        checkArgument(locationFinder instanceof Closure, "locationFinder must be a closure, but is "+locationFinder.class.getClass())
    }
    
    @Override
    public void setEntity(EntityLocal entity) {
        checkArgument(entity instanceof FollowTheSunPool, "Provided entity must be a FollowTheSunPool")
        super.setEntity(entity)
        this.poolEntity = (FollowTheSunPool) entity
        
        // Detect when containers are added to or removed from the pool.
        subscribe(poolEntity, FollowTheSunPool.CONTAINER_ADDED, eventHandler)
        subscribe(poolEntity, FollowTheSunPool.CONTAINER_REMOVED, eventHandler)
        subscribe(poolEntity, FollowTheSunPool.ITEM_ADDED, eventHandler)
        subscribe(poolEntity, FollowTheSunPool.ITEM_REMOVED, eventHandler)
        subscribe(poolEntity, FollowTheSunPool.ITEM_MOVED, eventHandler)
        
        // Take heed of any extant containers.
        for (Entity container : poolEntity.getContainerGroup().getMembers()) {
            onContainerAdded(container, false)
        }
        for (Entity item : poolEntity.getItemGroup().getMembers()) {
            onItemAdded(item, false)
        }

        scheduleLatencyReductionJig()
    }
    
    @Override
    public void suspend() {
        // TODO unsubscribe from everything? And resubscribe on resume?
        super.suspend();
        if (executor != null) executor.shutdownNow();
        executorQueued.set(false)
    }
    
    @Override
    public void resume() {
        super.resume();
        executor = Executors.newSingleThreadExecutor()
        executorQueued.set(false)
    }
    
    private scheduleLatencyReductionJig() {
        if (isRunning() && executorQueued.compareAndSet(false, true)) {
            
            executor.submit( {
                try {
                    executorQueued.set(false)
                    strategy.rebalance()
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt() // gracefully stop
                } catch (Exception e) {
                    if (isRunning()) {
                        LOG.error("Error during latency-reduction-jig", e)
                    } else {
                        LOG.debug("Error during latency-reduction-jig, but no longer running", e)
                    }
                }
            } )
        }
    }
    
    private void onContainerAdded(Entity container, boolean rebalanceNow) {
        subscribe(container, Attributes.LOCATION_CHANGED, eventHandler)
        Location location = locationFinder.call(container)
        
        if (LOG.isTraceEnabled()) LOG.trace("{} recording addition of container {} in location {}", this, container, location)
        model.onContainerAdded(container, location)
        
        if (rebalanceNow) scheduleLatencyReductionJig()
    }
    
    private void onContainerRemoved(Entity container, boolean rebalanceNow) {
        if (LOG.isTraceEnabled()) LOG.trace("{} recording removal of container {}", this, container)
        model.onContainerRemoved(container)
        if (rebalanceNow) scheduleLatencyReductionJig()
    }
    
    private void onItemAdded(Entity item, boolean rebalanceNow) {
        checkArgument(item instanceof Movable, "Added item $item must implement Movable")
        Entity parentContainer = item.getAttribute(Movable.CONTAINER)
        
        if (LOG.isTraceEnabled()) LOG.trace("{} recording addition of item {} in container {}", this, item, parentContainer)
        
        subscribe(item, itemUsageMetric, eventHandler)
        
        // Update the model, including the current metric value (if any).
        Map<? extends Entity, Double> currentValue = item.getAttribute(itemUsageMetric)
        boolean immovable = item.getConfig(Movable.IMMOVABLE)?:false
        model.onItemAdded(item, parentContainer, immovable)

        if (currentValue != null) {
            model.onItemUsageUpdated(item, currentValue)
        }
        
        if (rebalanceNow) scheduleLatencyReductionJig()
    }
    
    private void onItemRemoved(Entity item, boolean rebalanceNow) {
        if (LOG.isTraceEnabled()) LOG.trace("{} recording removal of item {}", this, item)
        unsubscribe(item)
        model.onItemRemoved(item)
        if (rebalanceNow) scheduleLatencyReductionJig()
    }
    
    private void onItemMoved(Entity item, Entity parentContainer, boolean rebalanceNow) {
        if (LOG.isTraceEnabled()) LOG.trace("{} recording moving of item {} to {}", this, item, parentContainer)
        model.onItemMoved(item, parentContainer)
        if (rebalanceNow) scheduleLatencyReductionJig()
    }
    
    private void onContainerLocationUpdated(Entity container, boolean rebalanceNow) {
        Location location = locationFinder.call(container)
        if (LOG.isTraceEnabled()) LOG.trace("{} recording location for container {}, new value {}", this, container, location)
        model.onContainerLocationUpdated(container, location)
        if (rebalanceNow) scheduleLatencyReductionJig()
    }
    
    private void onItemMetricUpdated(Entity item, Map<? extends Entity, Double> newValues, boolean rebalanceNow) {
        if (LOG.isTraceEnabled()) LOG.trace("{} recording usage update for item {}, new value {}", this, item, newValues)
        model.onItemUsageUpdated(item, newValues)
        if (rebalanceNow) scheduleLatencyReductionJig()
    }
}
