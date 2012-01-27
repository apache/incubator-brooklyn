package brooklyn.policy.loadbalancing

import java.util.Map

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Entity
import brooklyn.entity.Group
import brooklyn.entity.basic.AbstractEntity
import brooklyn.entity.basic.AbstractGroup;
import brooklyn.entity.trait.Changeable;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener
import brooklyn.event.basic.BasicNotificationSensor;


public class BalanceableWorkerPool extends AbstractEntity {
    
    public static BasicNotificationSensor<Entity> CONTAINER_ADDED = new BasicNotificationSensor<Entity>(
        Entity.class, "balanceablepool.container.added", "Container added to balanceable pool");
    public static BasicNotificationSensor<Entity> CONTAINER_REMOVED = new BasicNotificationSensor<Entity>(
        Entity.class, "balanceablepool.container.removed", "Container removed from balanceable pool");
    public static BasicNotificationSensor<Entity> ITEM_ADDED = new BasicNotificationSensor<Entity>(
        Entity.class, "balanceablepool.item.added", "Item added to balanceable pool");
    public static BasicNotificationSensor<Entity> ITEM_REMOVED = new BasicNotificationSensor<Entity>(
        Entity.class, "balanceablepool.item.removed", "Item removed from balanceable pool");
    
    // TODO: too-hot / too-cold sensors?
    //       "surplus" and "shortfall"?
    
    private static final Logger logger = LoggerFactory.getLogger(BalanceableWorkerPool)
    private Group containerGroup
    private Group itemGroup
    
    private final SensorEventListener<Entity> eventHandler = new SensorEventListener<Entity>() {
        public void onEvent(SensorEvent<Entity> event) {
            Entity source = event.getSource()
            Entity value = event.getValue()
            switch (event.getSensor()) {
                case AbstractGroup.MEMBER_ADDED:
                    if (source == containerGroup) emit(CONTAINER_ADDED, value)
                    else if (source == itemGroup) emit(ITEM_ADDED, value)
                    break
                case AbstractGroup.MEMBER_REMOVED:
                    if (source == containerGroup) emit(CONTAINER_REMOVED, value)
                    else if (source == itemGroup) emit(ITEM_REMOVED, value)
                    break
            }
        }
    }
    
    public BalanceableWorkerPool(Map properties = [:], Entity owner = null) {
        super(properties, owner)
    }
    
    public void setContents(Group containerGroup, Group itemGroup) {
        this.containerGroup = containerGroup
        this.itemGroup = itemGroup
        
        subscribe(containerGroup, AbstractGroup.MEMBER_ADDED, eventHandler)
        subscribe(containerGroup, AbstractGroup.MEMBER_REMOVED, eventHandler)
        subscribe(itemGroup, AbstractGroup.MEMBER_ADDED, eventHandler)
        subscribe(itemGroup, AbstractGroup.MEMBER_REMOVED, eventHandler)
    }
    
    public Group getContainerGroup() { return containerGroup }
    public Group getItemGroup() { return itemGroup }
    
}
