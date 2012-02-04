package brooklyn.policy.loadbalancing;

import static com.google.common.base.Preconditions.checkNotNull

import java.util.Map

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Entity
import brooklyn.entity.basic.AbstractEntity
import brooklyn.event.AttributeSensor
import brooklyn.event.basic.BasicAttributeSensor


public class MockItemEntity extends AbstractEntity implements Movable {

    private static final Logger LOG = LoggerFactory.getLogger(MockItemEntity)
    
    public static final AttributeSensor<Integer> TEST_METRIC =
        new BasicAttributeSensor<Integer>(Integer.class, "test.metric", "Dummy workrate for test entities")
    
    private MockContainerEntity currentContainer;
    
    public MockItemEntity (Map props=[:], Entity owner=null) {
        super(props, owner)
    }
    
    public String getContainerId() {
        return currentContainer?.getId()
    }

    @Override
    public <T> T setAttribute(AttributeSensor<T> attribute, T val) {
        LOG.debug("Mocks: item $this setting $attribute to $val")
        return super.setAttribute(attribute, val)
    }

    // only moves if the containers will accept us (otherwise we'd lose the item!)
    public void move(Entity rawDestination) {
        LOG.debug("Mocks: moving item $this from $currentContainer to $rawDestination")
        checkNotNull(rawDestination)
        MockContainerEntity destination = (MockContainerEntity) rawDestination;
        MockContainerEntity previousContainer = currentContainer
        previousContainer?.lock()
        try {
            destination.lock()
            try {
                currentContainer?.removeItem(this)
                currentContainer = destination
                destination.addItem(this)
                setAttribute(CONTAINER, currentContainer)
            } finally {
                destination.unlock()
            }
        } finally {
            previousContainer?.unlock()
        }
    }
    
    public void stop() {
        // FIXME How best to indicate this has been entirely stopped, rather than just in-transit?
        LOG.debug("Mocks: stopping item $this (was in container $currentContainer)")
        currentContainer?.removeItem(this)
        currentContainer = null
    }
    
    public String toString() {
        return "MockItem["+getDisplayName()+"]"
    }
}
