package brooklyn.test.entity;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.location.Location;
import brooklyn.util.collections.MutableMap;

/**
 * Mock entity for testing.
 */
public class TestEntityImpl extends AbstractEntity implements TestEntity {
	protected static final Logger LOG = LoggerFactory.getLogger(TestEntityImpl.class);

	protected int sequenceValue = 0;
	protected AtomicInteger counter = new AtomicInteger(0);
	protected Map<?,?> constructorProperties;
	protected Map<?,?> configureProperties;

    public TestEntityImpl() {
        super();
    }
    public TestEntityImpl(Map properties) {
        this(properties, null);
    }
    public TestEntityImpl(Entity parent) {
        this(MutableMap.of(), parent);
    }
    public TestEntityImpl(Map properties, Entity parent) {
        super(properties, parent);
        this.constructorProperties = properties;
    }
    
    public AbstractEntity configure(Map flags) {
        this.configureProperties = flags;
        return super.configure(flags);
    }
    
    @Override
    public boolean isLegacyConstruction() {
        return super.isLegacyConstruction();
    }
    
    @Override
    public void myEffector() {
        if (LOG.isTraceEnabled()) LOG.trace("In myEffector for {}", this);
    }
    
    @Override
    public Object identityEffector(Object arg) {
        if (LOG.isTraceEnabled()) LOG.trace("In identityEffector for {}", this);
        return arg;
    }
    
    @Override
    public AtomicInteger getCounter() {
        return counter;
    }
    
    @Override
    public int getCount() {
        return counter.get();
    }

    @Override
    public Map<?,?> getConstructorProperties() {
        return constructorProperties;
    }
    
    @Override
    public Map<?,?> getConfigureProperties() {
        return configureProperties;
    }
    
    @Override
    public synchronized int getSequenceValue() {
        return sequenceValue;
    }

    @Override
    public synchronized void setSequenceValue(int value) {
        sequenceValue = value;
        setAttribute(SEQUENCE, value);
    }

    @Override
    public void start(Collection<? extends Location> locs) {
        LOG.trace("Starting {}", this);
        setAttribute(SERVICE_STATE, Lifecycle.STARTING);
        counter.incrementAndGet();
        addLocations(locs);
        setAttribute(SERVICE_STATE, Lifecycle.RUNNING);
    }

    @Override
    public void stop() { 
        LOG.trace("Stopping {}", this);
        setAttribute(SERVICE_STATE, Lifecycle.STOPPING);
        counter.decrementAndGet();
        setAttribute(SERVICE_STATE, Lifecycle.STOPPED);
    }

    @Override
    public void restart() {
        throw new UnsupportedOperationException();
    }
    
    /**
     * TODO Rename to addChild
     */
    @Override
    public <T extends Entity> T createChild(EntitySpec<T> spec) {
        return addChild(spec);
    }

    @Override
    public <T extends Entity> T createAndManageChild(EntitySpec<T> spec) {
        if (!getManagementSupport().isDeployed()) throw new IllegalStateException("Entity "+this+" not managed");
        T child = createChild(spec);
        getEntityManager().manage(child);
        return child;
    }
    
    @Override
    public String toString() {
        String id = getId();
        return getEntityType().getSimpleName()+"["+id.substring(Math.max(0, id.length()-8))+"]";
    }
    
    // TODO add more mock methods
}
