package brooklyn.policy.loadbalancing

import java.util.Map
import java.util.Map.Entry
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.ConfigKey
import brooklyn.entity.Entity
import brooklyn.entity.basic.AbstractEntity
import brooklyn.entity.basic.EntityLocal
import brooklyn.event.AttributeSensor
import brooklyn.event.Sensor
import brooklyn.event.SensorEvent
import brooklyn.event.SensorEventListener
import brooklyn.policy.basic.AbstractPolicy
import brooklyn.policy.loadbalancing.BalanceableWorkerPool.ContainerItemPair
import brooklyn.policy.resizing.ResizingPolicy
import brooklyn.util.flags.SetFromFlag

import com.google.common.base.Preconditions
import com.google.common.collect.ImmutableMap


/**
 * <p>Policy that is attached to a pool of "containers", each of which can host one or more migratable "items".
 * The policy monitors the workrates of the items and effects migrations in an attempt to ensure that the containers
 * are all sufficiently utilized without any of them being overloaded.
 * 
 * <p>The particular sensor that defines the items' workrates is specified when the policy is constructed. High- and
 * low-thresholds are defined as <strong>configuration keys</strong> on each of the container entities in the pool:
 * for an item sensor named <code>foo.bar.sensorName</code>, the corresponding container config keys would be named
 * <code>foo.bar.sensorName.threshold.low</code> and <code>foo.bar.sensorName.threshold.high</code>.
 * 
 * <p>In addition to balancing items among the available containers, this policy causes the pool Entity to emit
 * <code>POOL_COLD</code> and <code>POOL_HOT</code> events when it is determined that there is a surplus or shortfall
 * of container resource in the pool respectively. These events may be consumed by a separate policy that is capable
 * of resizing the container pool.
 */
public class LoadBalancingPolicy extends AbstractPolicy {
    
    private static final Logger LOG = LoggerFactory.getLogger(LoadBalancingPolicy.class)
    
    @SetFromFlag // TODO not respected for policies? I had to look this up in the constructor
    private long minPeriodBetweenExecs = 100
    
    private final AttributeSensor<? extends Number> metric
    private final String lowThresholdConfigKeyName
    private final String highThresholdConfigKeyName
    private final BalanceablePoolModel<Entity, Entity> model
    private final BalancingStrategy<Entity, ?> strategy
    private BalanceableWorkerPool poolEntity
    
    private volatile ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor()
    private final AtomicBoolean executorQueued = new AtomicBoolean(false)
    private volatile long executorTime = 0

    private int lastEmittedDesiredPoolSize = 0
    private String lastEmittedPoolTemperature = null // "cold" or "hot"
    
    private final SensorEventListener<?> eventHandler = new SensorEventListener<Object>() {
        public void onEvent(SensorEvent<?> event) {
            if (LOG.isTraceEnabled()) LOG.trace("{} received event {}", LoadBalancingPolicy.this, event)
            Entity source = event.getSource()
            Object value = event.getValue()
            Sensor sensor = event.getSensor()
            switch (sensor) {
                case metric:
                    onItemMetricUpdate(source, ((Number) value).doubleValue(), true)
                    break
                case BalanceableWorkerPool.CONTAINER_ADDED:
                    onContainerAdded((Entity) value, true)
                    break
                case BalanceableWorkerPool.CONTAINER_REMOVED:
                    onContainerRemoved((Entity) value, true)
                    break
                case BalanceableWorkerPool.ITEM_ADDED:
                    ContainerItemPair pair = value
                    onItemAdded(pair.item, pair.container, true)
                    break
                case BalanceableWorkerPool.ITEM_REMOVED:
                    ContainerItemPair pair = value
                    onItemRemoved(pair.item, pair.container, true)
                    break
                case BalanceableWorkerPool.ITEM_MOVED:
                    ContainerItemPair pair = value
                    onItemMoved(pair.item, pair.container, true)
                    break
            }
        }
    }
    
    public LoadBalancingPolicy(Map props = [:], AttributeSensor<? extends Number> metric,
            BalanceablePoolModel<? extends Entity, ? extends Entity> model) {
        
        super(props)
        this.metric = metric
        this.lowThresholdConfigKeyName = metric.getName()+".threshold.low"
        this.highThresholdConfigKeyName = metric.getName()+".threshold.high"
        this.model = model
        this.strategy = new BalancingStrategy<Entity, Object>(getName(), model) // TODO: extract interface, inject impl
        this.minPeriodBetweenExecs = props.minPeriodBetweenExecs ?: 100
    }
    
    @Override
    public void setEntity(EntityLocal entity) {
        Preconditions.checkArgument(entity instanceof BalanceableWorkerPool, "Provided entity must be a BalanceableWorkerPool")
        super.setEntity(entity)
        this.poolEntity = (BalanceableWorkerPool) entity
        
        // Detect when containers are added to or removed from the pool.
        subscribe(poolEntity, BalanceableWorkerPool.CONTAINER_ADDED, eventHandler)
        subscribe(poolEntity, BalanceableWorkerPool.CONTAINER_REMOVED, eventHandler)
        subscribe(poolEntity, BalanceableWorkerPool.ITEM_ADDED, eventHandler)
        subscribe(poolEntity, BalanceableWorkerPool.ITEM_REMOVED, eventHandler)
        subscribe(poolEntity, BalanceableWorkerPool.ITEM_MOVED, eventHandler)
        
        // Take heed of any extant containers.
        for (Entity container : poolEntity.getContainerGroup().getMembers()) {
            onContainerAdded(container, false)
        }
        for (Entity item : poolEntity.getItemGroup().getMembers()) {
            onItemAdded(item, item.getAttribute(Movable.CONTAINER), false)
        }

        scheduleRebalance()
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
        executor = Executors.newSingleThreadScheduledExecutor()
        executorTime = 0
        executorQueued.set(false)
    }
    
    private scheduleRebalance() {
        if (isRunning() && executorQueued.compareAndSet(false, true)) {
            long now = System.currentTimeMillis()
            long delay = Math.max(0, (executorTime + minPeriodBetweenExecs) - now)
            
            executor.schedule(
                {
                    try {
                        executorTime = System.currentTimeMillis()
                        executorQueued.set(false)
                        strategy.rebalance()
                        
                        if (LOG.isDebugEnabled()) LOG.debug("{} post-rebalance: poolSize={}; workrate={}; lowThreshold={}; " + 
                                "highThreshold={}", this, model.getPoolSize(), model.getCurrentPoolWorkrate(), 
                                model.getPoolLowThreshold(), model.getPoolHighThreshold())
                        
                        if (model.isCold()) {
                            Map eventVal = ImmutableMap.of(
                                    ResizingPolicy.POOL_CURRENT_SIZE_KEY, model.poolSize,
                                    ResizingPolicy.POOL_CURRENT_WORKRATE_KEY, model.getCurrentPoolWorkrate(),
                                    ResizingPolicy.POOL_LOW_THRESHOLD_KEY, model.getPoolLowThreshold(),
                                    ResizingPolicy.POOL_HIGH_THRESHOLD_KEY, model.getPoolHighThreshold())
            
                            poolEntity.emit(ResizingPolicy.POOL_COLD, eventVal)
                            
                            if (LOG.isInfoEnabled()) {
                                int desiredPoolSize = Math.ceil(model.getCurrentPoolWorkrate() / (model.getPoolLowThreshold()/model.poolSize)).intValue()
                                if (desiredPoolSize != lastEmittedDesiredPoolSize || lastEmittedPoolTemperature != "cold") {
                                    LOG.info("$this emitted COLD (suggesting $desiredPoolSize): $eventVal")
                                    lastEmittedDesiredPoolSize = desiredPoolSize
                                    lastEmittedPoolTemperature = "cold"
                                }
                            }
                        
                        } else if (model.isHot()) {
                            Map eventVal = ImmutableMap.of(
                                    ResizingPolicy.POOL_CURRENT_SIZE_KEY, model.poolSize,
                                    ResizingPolicy.POOL_CURRENT_WORKRATE_KEY, model.getCurrentPoolWorkrate(),
                                    ResizingPolicy.POOL_LOW_THRESHOLD_KEY, model.getPoolLowThreshold(),
                                    ResizingPolicy.POOL_HIGH_THRESHOLD_KEY, model.getPoolHighThreshold())
                            
                            poolEntity.emit(ResizingPolicy.POOL_HOT, eventVal);
                            
                            if (LOG.isInfoEnabled()) {
                                int desiredPoolSize = Math.ceil(model.getCurrentPoolWorkrate() / (model.getPoolHighThreshold()/model.poolSize)).intValue()
                                if (desiredPoolSize != lastEmittedDesiredPoolSize || lastEmittedPoolTemperature != "hot") {
                                    LOG.info("$this emitted HOT (suggesting $desiredPoolSize): $eventVal")
                                    lastEmittedDesiredPoolSize = desiredPoolSize
                                    lastEmittedPoolTemperature = "hot"
                                }
                            }
                        }
                                                                
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt() // gracefully stop
                    } catch (Exception e) {
                        if (isRunning()) {
                            LOG.error("Error rebalancing", e)
                        } else {
                            LOG.debug("Error rebalancing, but no longer running", e)
                        }
                    }
                },
                delay,
                TimeUnit.MILLISECONDS)
        }
    }
    
    // TODO Can get duplicate onContainerAdded events.
    //      I presume it's because we subscribe and then iterate over the extant containers.
    //      Solution would be for subscription to give you events for existing / current value(s).
    //      Also current impl messes up single-threaded updates model: the setEntity is a different thread than for subscription events.
    private void onContainerAdded(Entity newContainer, boolean rebalanceNow) {
        Preconditions.checkArgument(newContainer instanceof BalanceableContainer, "Added container must be a BalanceableContainer")
        if (LOG.isTraceEnabled()) LOG.trace("{} recording addition of container {}", this, newContainer)
        // Low and high thresholds for the metric we're interested in are assumed to be present
        // in the container's configuration.
        Number lowThreshold = (Number) findConfigValue((AbstractEntity) newContainer, lowThresholdConfigKeyName)
        Number highThreshold = (Number) findConfigValue((AbstractEntity) newContainer, highThresholdConfigKeyName)
        if (lowThreshold == null || highThreshold == null) {
            LOG.warn(
                "Balanceable container '"+newContainer+"' does not define low- and high- threshold configuration keys: '"+
                lowThresholdConfigKeyName+"' and '"+highThresholdConfigKeyName+"', skipping")
            return
        }
        
        model.onContainerAdded(newContainer, lowThreshold.doubleValue(), highThreshold.doubleValue())
        
        // Take heed of any extant items.
        for (Movable item : ((BalanceableContainer) newContainer).getBalanceableItems()) 
            onItemAdded((Entity) item, newContainer, false)
        
        if (rebalanceNow) scheduleRebalance()
    }
    
    private static Object findConfigValue(AbstractEntity entity, String configKeyName) {
        Map<ConfigKey, Object> config = entity.getAllConfig()
        for (Entry<ConfigKey, Object> entry : config.entrySet()) {
            if (configKeyName.equals(entry.getKey().getName()))
                return entry.getValue()
        }
        return null
    }
    
    // TODO Receiving duplicates of onContainerRemoved (e.g. when running LoadBalancingInmemorySoakTest)
    private void onContainerRemoved(Entity oldContainer, boolean rebalanceNow) {
        if (LOG.isTraceEnabled()) LOG.trace("{} recording removal of container {}", this, oldContainer)
        model.onContainerRemoved(oldContainer)
        if (rebalanceNow) scheduleRebalance()
    }
    
    private void onItemAdded(Entity item, Entity parentContainer, boolean rebalanceNow) {
        Preconditions.checkArgument(item instanceof Movable, "Added item $item must implement Movable")
        if (LOG.isTraceEnabled()) LOG.trace("{} recording addition of item {} in container {}", this, item, parentContainer)
        
        subscribe(item, metric, eventHandler)
        
        // Update the model, including the current metric value (if any).
        boolean immovable = item.getConfig(Movable.IMMOVABLE)?:false
        Number currentValue = item.getAttribute(metric)
        model.onItemAdded(item, parentContainer, immovable)
        if (currentValue != null)
            model.onItemWorkrateUpdated(item, currentValue.doubleValue())
        
        if (rebalanceNow) scheduleRebalance()
    }
    
    private void onItemRemoved(Entity item, Entity parentContainer, boolean rebalanceNow) {
        if (LOG.isTraceEnabled()) LOG.trace("{} recording removal of item {}", this, item)
        unsubscribe(item)
        model.onItemRemoved(item)
        if (rebalanceNow) scheduleRebalance()
    }
    
    private void onItemMoved(Entity item, Entity parentContainer, boolean rebalanceNow) {
        if (LOG.isTraceEnabled()) LOG.trace("{} recording moving of item {} to {}", this, item, parentContainer)
        model.onItemMoved(item, parentContainer)
        if (rebalanceNow) scheduleRebalance()
    }
    
    private void onItemMetricUpdate(Entity item, double newValue, boolean rebalanceNow) {
        if (LOG.isTraceEnabled()) LOG.trace("{} recording metric update for item {}, new value {}", this, item, newValue)
        model.onItemWorkrateUpdated(item, newValue)
        if (rebalanceNow) scheduleRebalance()
    }
    
    @Override
    public String toString() {
        return getClass().getSimpleName() + (name ? "("+name+")" : "")
    }
}
