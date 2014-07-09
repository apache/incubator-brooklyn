package brooklyn.policy.loadbalancing;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.Serializable;

import brooklyn.entity.Entity;
import brooklyn.entity.Group;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.entity.trait.Resizable;
import brooklyn.event.basic.BasicNotificationSensor;

/**
 * Represents an elastic group of "container" entities, each of which is capable of hosting "item" entities that perform
 * work and consume the container's available resources (e.g. CPU or bandwidth). Auto-scaling and load-balancing policies can
 * be attached to this pool to provide dynamic elasticity based on workrates reported by the individual item entities.
 */
@ImplementedBy(BalanceableWorkerPoolImpl.class)
public interface BalanceableWorkerPool extends Entity, Resizable {

    // FIXME Asymmetry between loadbalancing and followTheSun: ITEM_ADDED and ITEM_REMOVED in loadbalancing
    // are of type ContainerItemPair, but in followTheSun it is just the `Entity item`.
    
    /** Encapsulates an item and a container; emitted for {@code ITEM_ADDED}, {@code ITEM_REMOVED} and
     * {@code ITEM_MOVED} sensors.
     */
    public static class ContainerItemPair implements Serializable {
        private static final long serialVersionUID = 1L;
        public final BalanceableContainer<?> container;
        public final Entity item;
        
        public ContainerItemPair(BalanceableContainer<?> container, Entity item) {
            this.container = container;
            this.item = checkNotNull(item);
        }
        
        @Override
        public String toString() {
            return ""+item+" @ "+container;
        }
    }
    
    // Pool constituent notifications.
    public static BasicNotificationSensor<Entity> CONTAINER_ADDED = new BasicNotificationSensor<Entity>(
        Entity.class, "balanceablepool.container.added", "Container added to balanceable pool");
    public static BasicNotificationSensor<Entity> CONTAINER_REMOVED = new BasicNotificationSensor<Entity>(
        Entity.class, "balanceablepool.container.removed", "Container removed from balanceable pool");
    public static BasicNotificationSensor<ContainerItemPair> ITEM_ADDED = new BasicNotificationSensor<ContainerItemPair>(
        ContainerItemPair.class, "balanceablepool.item.added", "Item added to balanceable pool");
    public static BasicNotificationSensor<ContainerItemPair> ITEM_REMOVED = new BasicNotificationSensor<ContainerItemPair>(
        ContainerItemPair.class, "balanceablepool.item.removed", "Item removed from balanceable pool");
    public static BasicNotificationSensor<ContainerItemPair> ITEM_MOVED = new BasicNotificationSensor<ContainerItemPair>(
        ContainerItemPair.class, "balanceablepool.item.moved", "Item moved in balanceable pool to the given container");
    
    public void setResizable(Resizable resizable);
    
    public void setContents(Group containerGroup, Group itemGroup);
    
    public Group getContainerGroup();
    
    public Group getItemGroup();
}
