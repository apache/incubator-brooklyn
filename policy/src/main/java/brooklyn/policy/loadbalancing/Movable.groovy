package brooklyn.policy.loadbalancing;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Effector
import brooklyn.entity.Entity
import brooklyn.entity.basic.Description
import brooklyn.entity.basic.MethodEffector
import brooklyn.entity.basic.NamedParameter
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.event.basic.BasicConfigKey
import brooklyn.util.flags.SetFromFlag


/**
 * Represents an item that can be migrated between balanceable containers.
 */
public interface Movable extends Entity {
    
    @SetFromFlag("immovable")
    public static ConfigKey<Boolean> IMMOVABLE = new BasicConfigKey<Boolean>(
        Boolean.class, "movable.item.immovable", "Indicates whether this item instance is immovable, so cannot be moved by policies", false)
    
    public static BasicAttributeSensor<BalanceableContainer> CONTAINER = new BasicAttributeSensor<BalanceableContainer>(
        BalanceableContainer.class, "movable.item.container", "The container that this item is on");
    
    public static final Effector MOVE = new MethodEffector(Movable.&move);
    
    public String getContainerId();
    
    @Description("Moves this entity to the given container")
    public void move(@NamedParameter("destination") Entity destination);
    
}
