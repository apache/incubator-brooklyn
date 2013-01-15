package brooklyn.policy.loadbalancing;

import java.util.concurrent.locks.ReentrantLock

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.config.ConfigKey
import brooklyn.entity.Effector
import brooklyn.entity.Entity
import brooklyn.entity.basic.AbstractGroupImpl
import brooklyn.entity.basic.Attributes
import brooklyn.entity.basic.MethodEffector
import brooklyn.entity.trait.Startable
import brooklyn.event.AttributeSensor
import brooklyn.event.basic.BasicConfigKey
import brooklyn.location.Location
import brooklyn.util.flags.SetFromFlag

import com.google.common.collect.Iterables


public class MockContainerEntity extends AbstractGroupImpl implements BalanceableContainer<Entity>, Startable {

    private static final Logger LOG = LoggerFactory.getLogger(MockContainerEntity)

    @SetFromFlag("membership")
    public static final ConfigKey<String> MOCK_MEMBERSHIP =
            new BasicConfigKey<String>(String.class, "mock.container.membership", "For testing ItemsInContainersGroup")

    public static final Effector OFFLOAD_AND_STOP = new MethodEffector(MockContainerEntity.&offloadAndStop);

    final long delay;
    volatile boolean offloading;
    volatile boolean running;

    ReentrantLock _lock = new ReentrantLock();

    public MockContainerEntity (Map props=[:], Entity parent, long delay=0) {
        super(props, parent)
        this.delay = delay
    }

    public MockContainerEntity (Map props=[:], long delay=0) {
        super(props, null)
        this.delay = delay
    }

    @Override
    public <T> T setAttribute(AttributeSensor<T> attribute, T val) {
        if (LOG.isDebugEnabled()) LOG.debug("Mocks: container $this setting $attribute to $val")
        return super.setAttribute(attribute, val)
    }

    public void lock() {
        _lock.lock();
        if (!running) {
            _lock.unlock();
            throw new IllegalStateException("Container lock $this; it is not running")
        }
    }

    public void unlock() {
        _lock.unlock();
    }

    public int getWorkrate() {
        int result = 0
        for (Entity member : getMembers()) {
            Integer memberMetric = member.getAttribute(MockItemEntity.TEST_METRIC)
            result += ((memberMetric != null) ? memberMetric : 0);
        }
        return result
    }

    public void addItem(Entity item) {
        if (LOG.isDebugEnabled()) LOG.debug("Mocks: adding item $item to container $this")
        if (!running || offloading) throw new IllegalStateException("Container $displayName is not running; cannot add item $item")
        addMember(item)
        emit(BalanceableContainer.ITEM_ADDED, item)
    }

    public void removeItem(Entity item) {
        if (LOG.isDebugEnabled()) LOG.debug("Mocks: removing item $item from container $this")
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
    public void start(Collection<? extends Location> locs) {
        if (LOG.isDebugEnabled()) LOG.debug("Mocks: starting container $this")
        _lock.lock();
        try {
            if (delay > 0) Thread.sleep(delay)
            running = true;
            locations.addAll(locs)
            Location loc = Iterables.get(locs, 0)
            String locName = (loc.getName() != null) ? loc.getName() : loc.toString()
            emit(Attributes.LOCATION_CHANGED, null)
            setAttribute(SERVICE_UP, true);
        } finally {
            _lock.unlock();
        }
    }

    @Override
    public void stop() {
        if (LOG.isDebugEnabled()) LOG.debug("Mocks: stopping container $this")
        _lock.lock();
        try {
            running = false;
            if (delay > 0) Thread.sleep(delay)
            setAttribute(SERVICE_UP, false);
        } finally {
            _lock.unlock();
        }
    }

    @Override
    private void stopWithoutLock() {
        running = false;
        if (delay > 0) Thread.sleep(delay)
        setAttribute(SERVICE_UP, false);
    }

    public void offloadAndStop(MockContainerEntity otherContainer) {
        if (LOG.isDebugEnabled()) LOG.debug("Mocks: offloading container $this to $otherContainer (items $balanceableItems)")
        runWithLock([this, otherContainer]) {
            offloading = false;
            for (MockItemEntity item : balanceableItems) {
                item.moveNonEffector(otherContainer)
            }
            if (LOG.isDebugEnabled()) LOG.debug("Mocks: stopping offloaded container $this")
            stopWithoutLock();
        }
    }

    @Override
    public void restart() {
        if (LOG.isDebugEnabled()) LOG.debug("Mocks: restarting $this")
        throw new UnsupportedOperationException();
    }

    public static void runWithLock(List<MockContainerEntity> entitiesToLock, Closure c) {
        List<MockContainerEntity> entitiesToLockCopy = entitiesToLock.findAll {it != null}
        List<MockContainerEntity> entitiesLocked = []
        Collections.sort(entitiesToLockCopy, new Comparator<MockContainerEntity>() {
            public int compare(MockContainerEntity o1, MockContainerEntity o2) {
                return o1.getId().compareTo(o2.getId())
            }})

        try {
            entitiesToLockCopy.each {
                it.lock()
                entitiesLocked.add(it)
            }

            c.call()

        } finally {
            entitiesLocked.each {
                it.unlock()
            }
        }
    }
}
