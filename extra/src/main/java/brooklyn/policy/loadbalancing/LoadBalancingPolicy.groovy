package brooklyn.policy.loadbalancing

import java.util.Map

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.ConfigKey
import brooklyn.entity.Entity
import brooklyn.entity.Group
import brooklyn.entity.basic.AbstractEntity
import brooklyn.entity.basic.EntityLocal
import brooklyn.event.Sensor
import brooklyn.event.SensorEvent
import brooklyn.event.SensorEventListener
import brooklyn.policy.basic.AbstractPolicy

import com.google.common.base.Preconditions

public class LoadBalancingPolicy extends AbstractPolicy {
    
    private static final Logger LOG = LoggerFactory.getLogger(LoadBalancingPolicy.class)
    
    private final Sensor<? extends Number> metric
    private final String lowThresholdConfigKeyName
    private final String highThresholdConfigKeyName
    private final BalanceablePoolModel<Entity, Entity> model
    private final BalancingStrategy<Entity, ?> strategy
    private BalanceableWorkerPool poolEntity
    
    private final SensorEventListener<?> eventHandler = new SensorEventListener<Object>() {
        public void onEvent(SensorEvent<?> event) {
            Entity source = event.getSource()
            Object value = event.getValue()
            
            switch (event.getSensor()) {
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
                    Entity parentContainer = null // TODO
                    onItemAdded((Entity) value, parentContainer, true)
                    break
                case BalanceableWorkerPool.ITEM_REMOVED:
                    onItemRemoved((Entity) value, true)
                    break
            }
        }
    }
    
    public LoadBalancingPolicy(Map properties = [:],
        Sensor<? extends Number> metric, BalanceablePoolModel<? extends Entity, ? extends Entity> model) {
        
        super(properties)
        this.metric = metric
        this.lowThresholdConfigKeyName = metric.getNameParts().last()+".threshold.low"
        this.highThresholdConfigKeyName = metric.getNameParts().last()+".threshold.high"
        this.model = model
        this.strategy = new BalancingStrategy<Entity, Object>(properties.name, model) // TODO: extract interface, inject impl
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
        
        // Take heed of any extant containers.
        for (Entity container : getContainerGroup().getMembers())
            onContainerAdded(container, false)
        
        strategy.rebalance()
    }
    
    protected Group getContainerGroup() { return poolEntity.getContainerGroup() }
    protected Group getItemGroup() { return poolEntity.getItemGroup() }
    
    private void onContainerAdded(Entity newContainer, boolean rebalanceNow) {
        Preconditions.checkArgument(newContainer instanceof BalanceableContainer, "Added container must be a BalanceableContainer")
        
        // Low and high thresholds for the metric we're interested in are assumed to be present
        // in the container's configuration.
        Map<String, ConfigKey> configKeys = ((AbstractEntity) newContainer).getConfigKeys()
        ConfigKey<? extends Number> lowThresholdConfigKey = configKeys.get(lowThresholdConfigKeyName)
        ConfigKey<? extends Number> highThresholdConfigKey = configKeys.get(highThresholdConfigKeyName)
        if (lowThresholdConfigKey == null || highThresholdConfigKey == null) {
            LOG.warn(
                "Balanceable container '"+newContainer+"' does not define low- and high- threshold configuration keys: '"+
                lowThresholdConfigKeyName+"' and '"+highThresholdConfigKeyName+"', skipping")
            return
        }
        
        double lowThreshold = ((Number) newContainer.getConfig(lowThresholdConfigKey)).doubleValue()
        double highThreshold = ((Number) newContainer.getConfig(highThresholdConfigKey)).doubleValue()
        model.addContainer(newContainer, lowThreshold, highThreshold)
        
        // Take heed of any extant items.
        for (Movable item : ((BalanceableContainer) newContainer).getBalanceableItems()) 
            onItemAdded((Entity) item, false)
        
        if (rebalanceNow) strategy.rebalance()
    }
    
    private void onContainerRemoved(Entity oldContainer, boolean rebalanceNow) {
        model.removeContainer(oldContainer)
        if (rebalanceNow) strategy.rebalance()
    }
    
    private void onItemAdded(Entity item, Entity parentContainer, boolean rebalanceNow) {
        Preconditions.checkArgument(item instanceof Movable, "Added item must implement Movable")
        
        subscribe(item, metric, eventHandler)
        
        // Update the model, including the current metric value (if any).
        Number currentValue = item.getAttribute(metric)
        if (currentValue == null)
            model.addItem(item, parentContainer)
        else
            model.addItem(item, parentContainer, currentValue.doubleValue())
        
        if (rebalanceNow) strategy.rebalance()
    }
    
    private void onItemRemoved(Entity item, boolean rebalanceNow) {
        unsubscribe(item)
        model.removeItem(item)
        if (rebalanceNow) strategy.rebalance()
    }
    
    private void onItemMetricUpdate(Entity item, double newValue, boolean rebalanceNow) {
        model.updateItemWorkrate(item, newValue)
        if (rebalanceNow) strategy.rebalance()
    }
    
}
