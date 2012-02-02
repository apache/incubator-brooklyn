package brooklyn.policy.loadbalancing;

import java.util.Map

import brooklyn.entity.Entity
import brooklyn.entity.basic.AbstractEntity
import brooklyn.event.AttributeSensor
import brooklyn.event.basic.BasicAttributeSensor


public class MockItemEntity extends AbstractEntity implements Movable {
    
    public static final AttributeSensor<Integer> TEST_METRIC =
        new BasicAttributeSensor<Integer>(Integer.class, "test.metric", "Dummy workrate for test entities")
    
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
