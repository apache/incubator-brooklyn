package brooklyn.policy.loadbalancing;

import java.util.Set;

/**
 * Contains worker items that can be moved between this container and others to effect load balancing.
 * Membership of a balanceable container does not imply a parent-child relationship in the Brooklyn
 * management sense.
 * 
 * @author splodge
 */
public interface BalanceableContainer<ItemType extends Movable> {
    
    public Set<ItemType> getBalanceableItems();
    
}
