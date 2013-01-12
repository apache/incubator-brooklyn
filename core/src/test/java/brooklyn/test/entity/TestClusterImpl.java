package brooklyn.test.entity;

import brooklyn.entity.Entity;
import brooklyn.entity.group.DynamicClusterImpl;
import brooklyn.entity.trait.Startable;
import brooklyn.util.flags.SetFromFlag;

/**
* Mock cluster entity for testing.
*/
public class TestClusterImpl extends DynamicClusterImpl implements TestCluster {
    @SetFromFlag("initialSize")
    public int size;

    public TestClusterImpl() {
        super();
        setAttribute(Startable.SERVICE_UP, true);
    }
    
    public TestClusterImpl(Entity parent, int initialSize) {
        super(parent);
        size = initialSize;
        setAttribute(Startable.SERVICE_UP, true);
    }
    
    public TestClusterImpl(int initialSize) {
        super((Entity)null);
        size = initialSize;
    }
    
    @Override
    public Integer resize(Integer desiredSize) {
        this.size = desiredSize;
        return size;
    }
    
    @Override
    public Integer getCurrentSize() {
        return size;
    }
 }
