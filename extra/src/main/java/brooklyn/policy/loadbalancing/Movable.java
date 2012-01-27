package brooklyn.policy.loadbalancing;

import brooklyn.entity.Entity;


/**
 * Represents an item that can be migrated between entities.
 * 
 * @author splodge
 */
public interface Movable {
    
    // TDOO?
    // public Entity getCurrentContainer();
    
    public void move(Entity destination);
    
}
