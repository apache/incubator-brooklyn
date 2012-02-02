package brooklyn.policy.loadbalancing;

import groovy.lang.MetaClass

import java.util.Collection
import java.util.Map
import java.util.Set

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Entity
import brooklyn.entity.basic.AbstractGroup
import brooklyn.entity.trait.Startable
import brooklyn.location.Location


public class MockContainerEntity extends AbstractGroup implements BalanceableContainer<Entity>, Startable {
    
    private static final Logger LOG = LoggerFactory.getLogger(MockContainerEntity)
    
    final long delay;
    volatile boolean offloading;
    volatile boolean running;
    
    public MockContainerEntity (Map props=[:], Entity owner, long delay=0) {
        super(props, owner)
        this.delay = delay
    }
    
    public MockContainerEntity (Map props=[:], long delay=0) {
        super(props, null)
        this.delay = delay
    }
    
    public int getWorkrate() {
        int result = 0
        for (Entity member : getMembers()) {
            result += (member.getAttribute(MockItemEntity.TEST_METRIC) ?: 0)
        }
    }
    
    public void addItem(Entity item) {
        LOG.info("Adding item "+item+" to container "+this)
        if (!running || offloading) throw new IllegalStateException("Container $displayName is not running; cannot add item $item")
        addMember(item)
        emit(BalanceableContainer.ITEM_ADDED, item)
    }
    
    public void removeItem(Entity item) {
        LOG.info("Removing item "+item+" from container "+this)
        if (!running) throw new IllegalStateException("Container $displayName is not running; cannot remove item $item")
        removeMember(item)
        emit(BalanceableContainer.ITEM_REMOVED, item)
    }
    
    public Set<Entity> getBalanceableItems() {
        return new LinkedHashSet<Entity>(getMembers())
    }
    
    public String toString() {
        return "MockContainer["+getDisplayName()+"]"
    }

    @Override
    public void start(Collection<? extends Location> locations) {
        if (delay > 0) Thread.sleep(delay)
        running = true;
        setAttribute(SERVICE_UP, true);
    }

    @Override
    public void stop() {
        running = false;
        if (delay > 0) Thread.sleep(delay)
        setAttribute(SERVICE_UP, false);
    }

    public void offloadAndStop(MockContainerEntity otherContainer) {
        offloading = false;
        for (MockItemEntity item : balanceableItems) {
            item.move(otherContainer)
        }
        stop();
    }
    
    @Override
    public void restart() {
        throw new UnsupportedOperationException();
    }
}
