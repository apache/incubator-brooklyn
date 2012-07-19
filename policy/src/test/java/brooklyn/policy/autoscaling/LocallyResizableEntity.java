package brooklyn.policy.autoscaling;

import java.util.List;

import brooklyn.entity.LocallyManagedEntity;
import brooklyn.entity.trait.Resizable;
import brooklyn.entity.trait.Startable;
import brooklyn.test.entity.TestCluster;

import com.google.common.base.Throwables;
import com.google.common.collect.Lists;

/**
 * Test class for providing a Resizable LocallyManagedEntity for policy testing
 * It is hooked up to a TestCluster that can be used to make assertions against
 */
public class LocallyResizableEntity extends LocallyManagedEntity implements Resizable {
    private static final long serialVersionUID = 7394441878443491555L;
    
    List<Integer> sizes = Lists.newArrayList();
    TestCluster cluster;
    long resizeSleepTime = 0;
    
    public LocallyResizableEntity (TestCluster tc) {
        this.cluster = tc;
        setAttribute(Startable.SERVICE_UP, true);
    }
    
    public Integer resize(Integer newSize) {
        try {
            Thread.sleep(resizeSleepTime);
            sizes.add(newSize); 
            return (cluster.size = newSize);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw Throwables.propagate(e);
        }
    }
    
    public Integer getCurrentSize() {
        return cluster.size;
    }
    
    public String toString() {
        return getDisplayName();
    }
}
