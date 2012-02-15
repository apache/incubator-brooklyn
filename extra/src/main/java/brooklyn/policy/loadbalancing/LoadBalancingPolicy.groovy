package brooklyn.policy.loadbalancing

import java.util.Map
import java.util.Map.Entry
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
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

import com.google.common.base.Preconditions
import com.google.common.collect.ImmutableMap

public class LoadBalancingPolicy extends AbstractPolicy {
    
    private static final Logger LOG = LoggerFactory.getLogger(LoadBalancingPolicy.class)
    
    private final AttributeSensor<? extends Number> metric
    private final String lowThresholdConfigKeyName
    private final String highThresholdConfigKeyName
    private final BalanceablePoolModel<Entity, Entity> model
    private final BalancingStrategy<Entity, ?> strategy
    private BalanceableWorkerPool poolEntity
    private ExecutorService executor = Executors.newSingleThreadExecutor()
    private AtomicBoolean executorQueued = new AtomicBoolean(false)
    
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
    
    public LoadBalancingPolicy(Map properties = [:], AttributeSensor<? extends Number> metric,
        BalanceablePoolModel<? extends Entity, ? extends Entity> model) {
        
        super(properties)
        this.metric = metric
        this.lowThresholdConfigKeyName = metric.getName()+".threshold.low"
        this.highThresholdConfigKeyName = metric.getName()+".threshold.high"
        this.model = model
        this.strategy = new BalancingStrategy<Entity, Object>(getName(), model) // TODO: extract interface, inject impl
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
        executor = Executors.newSingleThreadExecutor()
        executorQueued.set(false)
    }
    
    private scheduleRebalance() {
        if (isRunning() && executorQueued.compareAndSet(false, true)) {
            
            executor.submit( {
                try {
                    executorQueued.set(false)
                    strategy.rebalance()
                    
                    if (LOG.isTraceEnabled()) LOG.trace("{} post-rebalance: poolSize={}; workrate={}; lowThreshold={}; " + 
                            "highThreshold={}", this, model.getPoolSize(), model.getCurrentPoolWorkrate(), 
                            model.getPoolLowThreshold(), model.getPoolHighThreshold())
                    
                    if (model.isCold()) {
                        poolEntity.emit(BalanceableWorkerPool.POOL_COLD, ImmutableMap.of(
                                ResizingPolicy.POOL_CURRENT_SIZE_KEY, model.poolSize,
                                ResizingPolicy.POOL_CURRENT_WORKRATE_KEY, model.getCurrentPoolWorkrate(),
                                ResizingPolicy.POOL_LOW_THRESHOLD_KEY, model.getPoolLowThreshold(),
                                ResizingPolicy.POOL_HIGH_THRESHOLD_KEY, model.getPoolHighThreshold()));
                    
                    } else if (model.isHot()) {
                        poolEntity.emit(BalanceableWorkerPool.POOL_HOT, ImmutableMap.of(
                                ResizingPolicy.POOL_CURRENT_SIZE_KEY, model.poolSize,
                                ResizingPolicy.POOL_CURRENT_WORKRATE_KEY, model.getCurrentPoolWorkrate(),
                                ResizingPolicy.POOL_LOW_THRESHOLD_KEY, model.getPoolLowThreshold(),
                                ResizingPolicy.POOL_HIGH_THRESHOLD_KEY, model.getPoolHighThreshold()));
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
            } )
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
        Number currentValue = item.getAttribute(metric)
        if (currentValue == null)
            model.onItemAdded(item, parentContainer)
        else
            model.onItemAdded(item, parentContainer, currentValue.doubleValue())
        
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
    
}
