package brooklyn.policy.loadbalancing;

import brooklyn.entity.Entity;


/**
 * Represents an item that can be migrated between entities.
 * 
 * @author splodge
 */
public interface Movable {
    
    public void move(Entity destination);
    
}
