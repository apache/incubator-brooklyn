package brooklyn.policy.loadbalancing;

import java.util.Set;

/**
 * TODO: javadoc
 * 
 * @author splodge
 */
public interface Balanceable<ItemType extends Movable> {
    
    public Set<ItemType> getItems();
    
}
