package brooklyn.policy.loadbalancing;

import java.util.Map;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractEntity;


public class MockItemEntity extends AbstractEntity implements Movable {
    private Entity currentContainer;
    
    public MockItemEntity (Map props=[:], Entity owner=null) {
        super(props, owner)
    }
    
    public String getContainerId() {
        return currentContainer?.getId()
    }
    
    public void move(Entity destination) {
        ((MockContainerEntity) currentContainer)?.removeItem(this)
        currentContainer = destination
        ((MockContainerEntity) currentContainer)?.addItem(this)
    }
    
    public void stop() {
        // FIXME How best to indicate this has been entirely stopped, rather than just in-transit?
        ((MockContainerEntity) currentContainer)?.removeItem(this)
    }
    
    public String toString() {
        return "MockItem["+getDisplayName()+"]"
    }
    
}
