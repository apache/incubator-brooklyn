package brooklyn.policy.loadbalancing;

import brooklyn.entity.ConfigKey
import brooklyn.entity.Effector
import brooklyn.entity.Entity
import brooklyn.entity.basic.MethodEffector
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.event.basic.BasicConfigKey


/**
 * Represents an item that can be migrated between balanceable containers.
 */
public interface Movable {
    
    public static ConfigKey<Boolean> IMMOVABLE = new BasicConfigKey<Boolean>(
        Boolean.class, "movable.item.immovable", "Indicates whether this item instance is immovable, so cannot be moved by policies", false)
    
    public static BasicAttributeSensor<BalanceableContainer> CONTAINER = new BasicAttributeSensor<BalanceableContainer>(
        BalanceableContainer.class, "movable.item.container", "The container that this item is on");
    
    public static final Effector MOVE = new MethodEffector(Movable.&move);
    
    public String getContainerId();
    public void move(Entity destination);
    
}
