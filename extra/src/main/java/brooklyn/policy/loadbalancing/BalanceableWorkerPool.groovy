package brooklyn.policy.loadbalancing

import static com.google.common.base.Preconditions.checkNotNull

import java.util.Map

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Entity
import brooklyn.entity.Group
import brooklyn.entity.basic.AbstractEntity
import brooklyn.entity.basic.AbstractGroup
import brooklyn.entity.trait.Startable
import brooklyn.event.Sensor
import brooklyn.event.SensorEvent
import brooklyn.event.SensorEventListener
import brooklyn.event.basic.BasicNotificationSensor


public class BalanceableWorkerPool extends AbstractEntity {

    private static final Logger LOG = LoggerFactory.getLogger(BalanceableWorkerPool.class)
    
    /** Encapsulates an item and a container; emitted for <code>ITEM_ADDED</code>, <code>ITEM_REMOVED</code> and
     * <code>ITEM_MOVED</code> sensors.
     */
    public static class ContainerItemPair implements Serializable {
        private static final long serialVersionUID = 1L;
        public final Entity container
        public final Entity item
        
        public ContainerItemPair(Entity container, Entity item) {
            this.container = container
            this.item = checkNotNull(item)
        }
        
        @Override
        public String toString() {
            return "$item @ $container"
        }
    }
    
    // Pool constituent notifications.
    public static BasicNotificationSensor<Entity> CONTAINER_ADDED = new BasicNotificationSensor<Entity>(
        Entity.class, "balanceablepool.container.added", "Container added to balanceable pool")
    public static BasicNotificationSensor<Entity> CONTAINER_REMOVED = new BasicNotificationSensor<Entity>(
        Entity.class, "balanceablepool.container.removed", "Container removed from balanceable pool")
    public static BasicNotificationSensor<ContainerItemPair> ITEM_ADDED = new BasicNotificationSensor<ContainerItemPair>(
        ContainerItemPair.class, "balanceablepool.item.added", "Item added to balanceable pool")
    public static BasicNotificationSensor<ContainerItemPair> ITEM_REMOVED = new BasicNotificationSensor<ContainerItemPair>(
        ContainerItemPair.class, "balanceablepool.item.removed", "Item removed from balanceable pool")
    public static BasicNotificationSensor<ContainerItemPair> ITEM_MOVED = new BasicNotificationSensor<ContainerItemPair>(
        ContainerItemPair.class, "balanceablepool.item.moved", "Item moved in balanceable pool to the given container")
    
    // Pool workrate notifications.
    public static BasicNotificationSensor<Map> POOL_HOT = new BasicNotificationSensor<Map>(
        Map.class, "balanceablepool.hot", "Pool has insufficient container resource for current workload")
    public static BasicNotificationSensor<Map> POOL_COLD = new BasicNotificationSensor<Map>(
        Map.class, "balanceablepool.cold", "Pool has too much container resource for current workload")
    
    
    private Group containerGroup
    private Group itemGroup
    
    private final SensorEventListener<?> eventHandler = new SensorEventListener<Object>() {
        public void onEvent(SensorEvent<?> event) {
            LOG.info("BalanceableWorkerPool.onEvent: {}", event)
            if (LOG.isTraceEnabled()) LOG.trace("{} received event {}", BalanceableWorkerPool.this, event)
            Entity source = event.getSource()
            Object value = event.getValue()
            Sensor sensor = event.getSensor()
            
            switch (sensor) {
                case AbstractGroup.MEMBER_ADDED:
                    if (source.equals(containerGroup)) {
                        onContainerAdded((Entity) value)
                    } else if (source.equals(itemGroup)) {
                        onItemAdded((Entity)value)
                    } else {
                        throw new IllegalStateException()
                    }
                    break
                case AbstractGroup.MEMBER_REMOVED:
                    if (source.equals(containerGroup)) {
                        onContainerRemoved((Entity) value)
                    } else if (source.equals(itemGroup)) {
                        onItemRemoved((Entity) value)
                    } else {
                        throw new IllegalStateException()
                    }
                    break
                case Startable.SERVICE_UP:
                    // TODO What if start has failed? Is there a sensor to indicate that?
                    if ((Boolean)value) {
                        onContainerUp((Entity) source)
                    } else {
                        onContainerDown((Entity) source)
                    }
                    break
                case Movable.CONTAINER:
                    onItemMoved(source, (Entity) value)
                    break
                default:
                    throw new IllegalStateException("Unhandled event type $sensor: $event")
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
        
        // Process extant containers and items
        for (Entity existingContainer : containerGroup.getMembers()) {
            onContainerAdded(existingContainer)
        }
        for (Entity existingItem : itemGroup.getMembers()) {
            onItemAdded((Entity)existingItem)
        }
    }
    
    public Group getContainerGroup() {
        return containerGroup
    }
    
    private void onContainerAdded(Entity newContainer) {
        subscribe(newContainer, Startable.SERVICE_UP, eventHandler)
        if (!(newContainer instanceof Startable) || newContainer.getAttribute(Startable.SERVICE_UP)) {
            onContainerUp(newContainer)
        }
    }
    
    private void onContainerUp(Entity newContainer) {
        emit(CONTAINER_ADDED, newContainer)
    }
    
    private void onContainerDown(Entity oldContainer) {
        emit(CONTAINER_REMOVED, oldContainer)
    }
    
    private void onContainerRemoved(Entity oldContainer) {
        unsubscribe(oldContainer)
        emit(CONTAINER_REMOVED, oldContainer)
    }
    
    private void onItemAdded(Entity item) {
        subscribe(item, Movable.CONTAINER, eventHandler)
        emit(ITEM_ADDED, new ContainerItemPair(item.getAttribute(Movable.CONTAINER), item))
    }
    
    private void onItemRemoved(Entity item) {
        unsubscribe(item)
        emit(ITEM_REMOVED, new ContainerItemPair(null, item))
    }
    
    private void onItemMoved(Entity item, Entity container) {
        emit(ITEM_MOVED, new ContainerItemPair(container, item))
    }
}
