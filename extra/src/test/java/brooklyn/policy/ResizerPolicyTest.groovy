package brooklyn.policy

import static org.testng.AssertJUnit.*
import groovy.transform.InheritConstructors

import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.group.DynamicCluster

class ResizerPolicyTest {

    ResizerPolicy policy

    @BeforeMethod()
    public void before() {
        policy = [null, null]
        policy.setMinSize 0
    }
    
    @Test
    public void testUpperBounds() {
        TestCluster tc = [1]
        policy.setMetricLowerBound 0
        policy.setMetricUpperBound 100
        assertEquals 1, policy.calculateDesiredSize(99)
        assertEquals 2, policy.calculateDesiredSize(101)
    }
    
    @Test
    public void testLowerBounds() {
        TestCluster tc = [1]
        policy.setMetricLowerBound 100
        policy.setMetricUpperBound 10000
        assertEquals 1, policy.calculateDesiredSize(101)
        assertEquals 0, policy.calculateDesiredSize(99)
    }
    
    @InheritConstructors
    private static class TestCluster extends DynamicCluster {
        
        public int size
        
        TestCluster(int initialSize) {
            size = initialSize
        }
        
        @Override
        public int getCurrentSize() {
            return size
        }
        
    }
    
    
}
