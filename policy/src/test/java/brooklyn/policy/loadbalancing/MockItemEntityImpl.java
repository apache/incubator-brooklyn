package brooklyn.policy.loadbalancing;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.event.AttributeSensor;
import brooklyn.util.collections.MutableList;


public class MockItemEntityImpl extends AbstractEntity implements MockItemEntity {

    private static final Logger LOG = LoggerFactory.getLogger(MockItemEntityImpl.class);
    
    public static AtomicInteger totalMoveCount = new AtomicInteger(0);
    
    public static AtomicLong lastMoveTime = new AtomicLong(-1);
    
    private volatile boolean stopped;
    private volatile MockContainerEntity currentContainer;
    
    private final ReentrantLock _lock = new ReentrantLock();
    
    @Override
    public String getContainerId() {
        return (currentContainer == null) ? null : currentContainer.getId();
    }

    @Override
    public boolean isStopped() {
        return stopped;
    }

    @Override
    public <T> T setAttribute(AttributeSensor<T> attribute, T val) {
        if (LOG.isDebugEnabled()) LOG.debug("Mocks: item {} setting {} to {}", new Object[] {this, attribute, val});
        return super.setAttribute(attribute, val);
    }

    @Override
    public void move(Entity destination) {
        totalMoveCount.incrementAndGet();
        lastMoveTime.set(System.currentTimeMillis());
        moveNonEffector(destination);
    }
    
    // only moves if the containers will accept us (otherwise we'd lose the item!)
    @Override
    public void moveNonEffector(Entity rawDestination) {
        if (LOG.isDebugEnabled()) LOG.debug("Mocks: moving item {} from {} to {}", new Object[] {this, currentContainer, rawDestination});
        checkNotNull(rawDestination);
        final MockContainerEntity previousContainer = currentContainer;
        final MockContainerEntity destination = (MockContainerEntity) rawDestination;
        
        MockContainerEntityImpl.runWithLock(MutableList.of(previousContainer, destination), new Runnable() { 
            @Override public void run() {
                _lock.lock();
                try {
                    if (stopped) throw new IllegalStateException("Item "+this+" is stopped; cannot move to "+destination);
                    if (currentContainer != null) currentContainer.removeItem(MockItemEntityImpl.this);
                    currentContainer = destination;
                    destination.addItem(MockItemEntityImpl.this);
                    setAttribute(CONTAINER, currentContainer);
                } finally {
                    _lock.unlock();
                }
        }});
    }
    
    @Override
    public void stop() {
        // FIXME How best to indicate this has been entirely stopped, rather than just in-transit?
        if (LOG.isDebugEnabled()) LOG.debug("Mocks: stopping item {} (was in container {})", this, currentContainer);
        _lock.lock();
        try {
            if (currentContainer != null) currentContainer.removeItem(this);
            currentContainer = null;
            stopped = true;
        } finally {
            _lock.unlock();
        }
    }
    
    @Override
    public String toString() {
        return "MockItem["+getDisplayName()+"]";
    }
}
