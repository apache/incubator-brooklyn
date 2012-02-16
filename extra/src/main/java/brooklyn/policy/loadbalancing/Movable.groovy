package brooklyn.policy.loadbalancing;

import brooklyn.entity.Effector
import brooklyn.entity.Entity
import brooklyn.entity.basic.MethodEffector
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.event.basic.BasicNotificationSensor


/**
 * Represents an item that can be migrated between balanceable containers.
 */
public interface Movable {
    
    public static BasicAttributeSensor<BalanceableContainer> CONTAINER = new BasicAttributeSensor<BalanceableContainer>(
        BalanceableContainer.class, "movable.item.container", "The container that this item is on");
    
    public static final Effector MOVE = new MethodEffector(Movable.&move);
    
    public String getContainerId();
    public void move(Entity destination);
    
}
