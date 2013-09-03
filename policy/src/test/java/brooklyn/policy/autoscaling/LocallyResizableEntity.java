package brooklyn.policy.autoscaling;

import java.util.List;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.trait.Resizable;
import brooklyn.entity.trait.Startable;
import brooklyn.test.entity.TestCluster;

import com.google.common.base.Throwables;
import com.google.common.collect.Lists;

/**
 * Test class for providing a Resizable LocallyManagedEntity for policy testing
 * It is hooked up to a TestCluster that can be used to make assertions against
 */
public class LocallyResizableEntity extends AbstractEntity implements Resizable {
    List<Integer> sizes = Lists.newArrayList();
    TestCluster cluster;
    long resizeSleepTime = 0;
    
    public LocallyResizableEntity (TestCluster tc) {
        this(null, tc);
    }
    public LocallyResizableEntity (Entity parent, TestCluster tc) {
        super(parent);
        this.cluster = tc;
        setAttribute(Startable.SERVICE_UP, true);
    }
    
    @Override
    public Integer resize(Integer newSize) {
        try {
            Thread.sleep(resizeSleepTime);
            sizes.add(newSize); 
            return cluster.resize(newSize);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw Throwables.propagate(e);
        }
    }
    
    @Override
    public Integer getCurrentSize() {
        return cluster.getCurrentSize();
    }
    
    @Override
    public String toString() {
        return getDisplayName();
    }
}
