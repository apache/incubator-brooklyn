package brooklyn.policy.loadbalancing

import java.util.Map

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.ConfigKey
import brooklyn.entity.Entity
import brooklyn.entity.Group
import brooklyn.entity.basic.AbstractEntity
import brooklyn.entity.basic.AbstractGroup
import brooklyn.entity.basic.EntityLocal
import brooklyn.event.AttributeSensor
import brooklyn.event.SensorEvent
import brooklyn.event.SensorEventListener
import brooklyn.policy.basic.AbstractPolicy

import com.google.common.base.Preconditions

public class LoadBalancingPolicy extends AbstractPolicy implements SensorEventListener<Object> {
    
    private static final Logger LOG = LoggerFactory.getLogger(LoadBalancingPolicy.class)
    
    private final AttributeSensor<? extends Number> metric
    private final String lowThresholdConfigKeyName
    private final String highThresholdConfigKeyName
    private final BalanceablePoolModel<Entity, Entity> model
    private final BalancingStrategy<Entity, ?> strategy
    private Group containerPool
    
    
    public LoadBalancingPolicy(Map properties = [:],
        AttributeSensor<? extends Number> metric, BalanceablePoolModel<Entity, Entity> model) {
        
        super(properties)
        this.metric = metric
        this.lowThresholdConfigKeyName = metric.getNameParts().last()+".threshold.low"
        this.highThresholdConfigKeyName = metric.getNameParts().last()+".threshold.high"
        this.model = model
        this.strategy = new BalancingStrategy<Entity, Object>(properties.name, model) // TODO: extract interface, inject impl
    }
    
    @Override
    public void setEntity(EntityLocal entity) {
        Preconditions.checkArgument(entity instanceof Group, "Provided entity must be a Group of balanceable containers")
        super.setEntity(entity)
        this.containerPool = (Group) entity
        
        // Detect when containers are added to or removed from the pool.
        subscribe(containerPool, AbstractGroup.MEMBER_ADDED, this)
        subscribe(containerPool, AbstractGroup.MEMBER_REMOVED, this)
        
        // Take heed of any extant containers.
        for (Entity container : containerPool.getMembers())
            onContainerAdded(container, false)
        
        strategy.rebalance()
    }
    
    public void onEvent(SensorEvent<?> event) {
        // Silently swallow events received after this policy has been destroyed.
        if (isDestroyed())
            return
        
        Entity source = event.getSource()
        Object value = event.getValue()
        
        switch (event.getSensor()) {
            case metric:
                onItemMetricUpdate(source, ((Number) value).doubleValue(), true)
                break
            case AbstractGroup.MEMBER_ADDED:
                if (source == containerPool) onContainerAdded((Entity) value, true)
                else onItemAdded((Entity) value, true)
                break
            case AbstractGroup.MEMBER_REMOVED:
                if (source == containerPool) onContainerRemoved((Entity) value, true)
                else onItemRemoved((Entity) value, true)
                break
        }
    }
    
    private void onContainerAdded(Entity newContainer, boolean rebalance) {
        Preconditions.checkArgument(newContainer instanceof Balanceable, "Added container must implement Balanceable")
        
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
        for (Movable item : ((Balanceable) newContainer).getBalanceableItems()) 
            onItemAdded((Entity) item, false)
        
        if (rebalance) strategy.rebalance()
    }
    
    private void onContainerRemoved(Entity oldContainer, boolean rebalance) {
        model.removeContainer(oldContainer)
        if (rebalance) strategy.rebalance()
    }
    
    private void onItemAdded(Entity item, Entity parentContainer, boolean rebalance) {
        Preconditions.checkArgument(item instanceof Movable, "Added item must implement Movable")
        
        subscribe(item, metric, this)
        
        // Update the model, including the current metric value (if any).
        Number currentValue = item.getAttribute(metric)
        if (currentValue == null)
            model.addItem(item, parentContainer)
        else
            model.addItem(item, parentContainer, currentValue.doubleValue())
        
        if (rebalance) strategy.rebalance()
    }
    
    private void onItemRemoved(Entity item, boolean rebalance) {
        unsubscribe(item)
        model.removeItem(item)
        if (rebalance) strategy.rebalance()
    }
    
    private void onItemMetricUpdate(Entity item, double newValue, boolean rebalance) {
        model.updateItemWorkrate(item, newValue)
        if (rebalance) strategy.rebalance()
    }
    
}
