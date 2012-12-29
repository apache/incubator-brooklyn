package brooklyn.policy.loadbalancing;

import static com.google.common.base.Preconditions.checkNotNull

import java.util.Map
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock

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
    
    public static AtomicInteger totalMoveCount = new AtomicInteger(0)
    
    private volatile boolean stopped;
    private volatile MockContainerEntity currentContainer;
    
    private final ReentrantLock _lock = new ReentrantLock();
    
    public MockItemEntity (Map props=[:], Entity parent=null) {
        super(props, parent)
    }
    
    public String getContainerId() {
        return currentContainer?.getId()
    }

    public boolean isStopped() {
        return stopped
    }

    @Override
    public <T> T setAttribute(AttributeSensor<T> attribute, T val) {
        if (LOG.isDebugEnabled()) LOG.debug("Mocks: item $this setting $attribute to $val")
        return super.setAttribute(attribute, val)
    }

    @Override
    public void move(Entity destination) {
        totalMoveCount.incrementAndGet()
        moveNonEffector(destination)
    }
    
    // only moves if the containers will accept us (otherwise we'd lose the item!)
    public void moveNonEffector(Entity rawDestination) {
        if (LOG.isDebugEnabled()) LOG.debug("Mocks: moving item $this from $currentContainer to $rawDestination")
        checkNotNull(rawDestination)
        MockContainerEntity previousContainer = currentContainer
        MockContainerEntity destination = (MockContainerEntity) rawDestination;
        
        MockContainerEntity.runWithLock([previousContainer, destination]) {
            _lock.lock()
            try {
                if (stopped) throw new IllegalStateException("Item $this is stopped; cannot move to $destination")
                currentContainer?.removeItem(this)
                currentContainer = destination
                destination.addItem(this)
                setAttribute(CONTAINER, currentContainer)
            } finally {
                _lock.unlock()
            }
        }
    }
    
    public void stop() {
        // FIXME How best to indicate this has been entirely stopped, rather than just in-transit?
        if (LOG.isDebugEnabled()) LOG.debug("Mocks: stopping item $this (was in container $currentContainer)")
        _lock.lock()
        try {
            currentContainer?.removeItem(this)
            currentContainer = null
            stopped = true
        } finally {
            _lock.unlock()
        }
    }
    
    public String toString() {
        return "MockItem["+getDisplayName()+"]"
    }
}
