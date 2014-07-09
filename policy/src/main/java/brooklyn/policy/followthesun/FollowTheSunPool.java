package brooklyn.policy.followthesun;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.Serializable;

import brooklyn.entity.Entity;
import brooklyn.entity.Group;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.entity.trait.Resizable;
import brooklyn.event.basic.BasicNotificationSensor;

@ImplementedBy(FollowTheSunPoolImpl.class)
public interface FollowTheSunPool extends Entity, Resizable {

    // FIXME Remove duplication from BalanceableWorkerPool?

    // FIXME Asymmetry between loadbalancing and followTheSun: ITEM_ADDED and ITEM_REMOVED in loadbalancing
    // are of type ContainerItemPair, but in followTheSun it is just the `Entity item`.
    
    /** Encapsulates an item and a container; emitted by sensors.
     */
    public static class ContainerItemPair implements Serializable {
        private static final long serialVersionUID = 1L;
        public final Entity container;
        public final Entity item;

        public ContainerItemPair(Entity container, Entity item) {
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
            Entity.class, "followthesun.container.added", "Container added");
    public static BasicNotificationSensor<Entity> CONTAINER_REMOVED = new BasicNotificationSensor<Entity>(
            Entity.class, "followthesun.container.removed", "Container removed");
    public static BasicNotificationSensor<Entity> ITEM_ADDED = new BasicNotificationSensor<Entity>(
            Entity.class, "followthesun.item.added", "Item added");
    public static BasicNotificationSensor<Entity> ITEM_REMOVED = new BasicNotificationSensor<Entity>(
            Entity.class, "followthesun.item.removed", "Item removed");
    public static BasicNotificationSensor<ContainerItemPair> ITEM_MOVED = new BasicNotificationSensor<ContainerItemPair>(
            ContainerItemPair.class, "followthesun.item.moved", "Item moved to the given container");

    public void setContents(Group containerGroup, Group itemGroup);

    public Group getContainerGroup();

    public Group getItemGroup();
}
