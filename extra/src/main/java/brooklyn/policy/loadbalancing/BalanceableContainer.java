package brooklyn.policy.loadbalancing;

import java.util.Set;

import brooklyn.entity.Entity;
import brooklyn.event.basic.BasicNotificationSensor;

/**
 * Contains worker items that can be moved between this container and others to effect load balancing.
 * Membership of a balanceable container does not imply a parent-child relationship in the Brooklyn
 * management sense.
 * 
 * @author splodge
 */
public interface BalanceableContainer<ItemType extends Movable> {
    
    public static BasicNotificationSensor<Entity> ITEM_ADDED = new BasicNotificationSensor<Entity>(
            Entity.class, "balanceablecontainer.item.added", "Movable item added to balanceable container");
    public static BasicNotificationSensor<Entity> ITEM_REMOVED = new BasicNotificationSensor<Entity>(
            Entity.class, "balanceablecontainer.item.removed", "Movable item removed from balanceable container");
    
    
    public Set<ItemType> getBalanceableItems();
    
}
