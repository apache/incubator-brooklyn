package org.overpaas.policy;

import java.util.Collection;

/**
 * An entity that can have things moved around to "balance" it (e.g. a Monterey mediator tier 
 * could have segments mBalances a set of "movable items" across a set of "sub-containers". For example,
 * the items could be segments and the sub-containers could be mediators; the 
 * balanceable entity moves segments between mediators to keep the load balanced.
 *  
 * @author aled
 */
public interface BalanceableEntity extends Entity { // extends Entity, Balanceable

    Collection<Entity> getBalanceableSubContainers(); //getContainersToBalanceAcross()
    
    Collection<MoveableEntity> getMovableItems();
    
    Collection<MoveableEntity> getMovableItemsAt(Entity subContainer);
    
    void move(MoveableEntity item, Entity targetContainer);
}
