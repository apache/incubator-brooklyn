package brooklyn.policy.loadbalancing;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.annotation.Effector;
import brooklyn.entity.annotation.EffectorParam;
import brooklyn.entity.basic.MethodEffector;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.util.flags.SetFromFlag;


/**
 * Represents an item that can be migrated between balanceable containers.
 */
public interface Movable extends Entity {
    
    @SetFromFlag("immovable")
    public static ConfigKey<Boolean> IMMOVABLE = new BasicConfigKey<Boolean>(
        Boolean.class, "movable.item.immovable", "Indicates whether this item instance is immovable, so cannot be moved by policies", false);
    
    public static BasicAttributeSensor<BalanceableContainer> CONTAINER = new BasicAttributeSensor<BalanceableContainer>(
        BalanceableContainer.class, "movable.item.container", "The container that this item is on");
    
    public static final MethodEffector<Void> MOVE = new MethodEffector<Void>(Movable.class, "move");
    
    public String getContainerId();
    
    @Effector(description="Moves this entity to the given container")
    public void move(@EffectorParam(name="destination") Entity destination);
    
}
