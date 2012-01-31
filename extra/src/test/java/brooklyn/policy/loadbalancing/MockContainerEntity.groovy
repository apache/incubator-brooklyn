package brooklyn.policy.loadbalancing;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory

import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractGroup;


public class MockContainerEntity extends AbstractGroup implements BalanceableContainer<Entity> {
    
    private static final Logger LOG = LoggerFactory.getLogger(MockContainerEntity)
    
    
    public MockContainerEntity (Map props=[:], Entity owner=null) {
        super(props, owner)
    }
    
    public void addItem(Entity item) {
        LOG.info("Adding item "+item+" to container "+this)
        addMember(item)
        emit(BalanceableContainer.ITEM_ADDED, item)
    }
    
    public void removeItem(Entity item) {
        LOG.info("Removing item "+item+" from container "+this)
        removeMember(item)
        emit(BalanceableContainer.ITEM_REMOVED, item)
    }
    
    public Set<Entity> getBalanceableItems() {
        Set<Entity> result = new HashSet<Entity>()
        result.addAll(getMembers())
        return result
    }
    
    public String toString() {
        return "MockContainer["+getDisplayName()+"]"
    }
    
}
