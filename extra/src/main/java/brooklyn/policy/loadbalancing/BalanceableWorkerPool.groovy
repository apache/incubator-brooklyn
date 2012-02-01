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
    
    public static class ContainerItemPair {
        public final Entity container;
        public final Entity item;
        
        public ContainerItemPair(Entity container, Entity item) {
            this.container = container;
            this.item = item;
        }
    }
    
    public static BasicNotificationSensor<Entity> CONTAINER_ADDED = new BasicNotificationSensor<Entity>(
        Entity.class, "balanceablepool.container.added", "Container added to balanceable pool");
    public static BasicNotificationSensor<Entity> CONTAINER_REMOVED = new BasicNotificationSensor<Entity>(
        Entity.class, "balanceablepool.container.removed", "Container removed from balanceable pool");
    public static BasicNotificationSensor<ContainerItemPair> ITEM_ADDED = new BasicNotificationSensor<ContainerItemPair>(
        ContainerItemPair.class, "balanceablepool.item.added", "Item added to balanceable pool");
    public static BasicNotificationSensor<ContainerItemPair> ITEM_REMOVED = new BasicNotificationSensor<ContainerItemPair>(
        ContainerItemPair.class, "balanceablepool.item.removed", "Item removed from balanceable pool");
    public static BasicNotificationSensor<ContainerItemPair> ITEM_MOVED = new BasicNotificationSensor<ContainerItemPair>(
        ContainerItemPair.class, "balanceablepool.item.removed", "Item moved in balanceable pool to the given container");

    // TODO: too-hot / too-cold sensors?
    //       "surplus" and "shortfall"?
    
    private Group containerGroup
    
    private final SensorEventListener<?> eventHandler = new SensorEventListener<Object>() {
        public void onEvent(SensorEvent<?> event) {
            Entity source = event.getSource()
            Object value = event.getValue()
            switch (event.getSensor()) {
                case AbstractGroup.MEMBER_ADDED:
                    onContainerAdded((Entity) value)
                    break
                case AbstractGroup.MEMBER_REMOVED:
                    onContainerRemoved((Entity) value)
                    break
                case BalanceableContainer.ITEM_ADDED:
                    onItemAdded(source, (Entity) value)
                    break
                case BalanceableContainer.ITEM_REMOVED:
                    onItemRemoved(source, (Entity) value)
                    break
            }
        }
    }
    
    public BalanceableWorkerPool(Map properties = [:], Entity owner = null) {
        super(properties, owner)
    }
    
    public void setContents(Group containerGroup) {
        this.containerGroup = containerGroup
        subscribe(containerGroup, AbstractGroup.MEMBER_ADDED, eventHandler)
        subscribe(containerGroup, AbstractGroup.MEMBER_REMOVED, eventHandler)
        
        // Process extant containers.
        for (Entity existingContainer : containerGroup.getMembers())
            onContainerAdded(existingContainer)
    }
    
    public Group getContainerGroup() {
        return containerGroup;
    }
    
    private void onContainerAdded(Entity newContainer) {
        subscribe(newContainer, BalanceableContainer.ITEM_ADDED, eventHandler)
        subscribe(newContainer, BalanceableContainer.ITEM_REMOVED, eventHandler)
        emit(CONTAINER_ADDED, newContainer)
    }
    
    private void onContainerRemoved(Entity oldContainer) {
        // TODO: unsubscribe(oldContainer)
        emit(CONTAINER_REMOVED, oldContainer)
    }
    
    private void onItemAdded(Entity container, Entity item) {
        emit(ITEM_ADDED, new ContainerItemPair(container, item))
    }
    
    private void onItemRemoved(Entity container, Entity item) {
        emit(ITEM_REMOVED, new ContainerItemPair(container, item))
    }
    
}
