package brooklyn.policy.loadbalancing;

import brooklyn.entity.Effector;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.MethodEffector;


/**
 * Represents an item that can be migrated between balanceable containers.
 * 
 * @author splodge
 */
public interface Movable {
    
    public static final Effector MOVE = new MethodEffector(Movable.&move);
    
    // TODO?
    // public Entity getCurrentContainer();
    
    public void move(Entity destination);
    
}
