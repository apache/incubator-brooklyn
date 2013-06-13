package brooklyn.test.entity;

import brooklyn.entity.group.DynamicClusterImpl;
import brooklyn.entity.trait.Startable;

/**
* Mock cluster entity for testing.
*/
public class TestClusterImpl extends DynamicClusterImpl implements TestCluster {
    private volatile int size;

    public TestClusterImpl() {
    }
    
    @Override
    public void init() {
        super.init();
        size = getConfig(INITIAL_SIZE);
        setAttribute(Startable.SERVICE_UP, true);
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
