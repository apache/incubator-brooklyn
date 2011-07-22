package brooklyn.policy

import static org.testng.AssertJUnit.*
import groovy.transform.InheritConstructors

import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.group.DynamicCluster

class ResizerPolicyTest {

    ResizerPolicy policy
    
    @BeforeMethod()
    public void before() {
        policy = new ResizerPolicy(null)
        policy.setMinSize 0
    }
    
    @Test
    public void testUpperBounds() {
        TestCluster tc = [1]
        policy.@entity = tc
        policy.setMetricLowerBound 0
        policy.setMetricUpperBound 100
        assertEquals 1, policy.calculateDesiredSize(99)
        assertEquals 1, policy.calculateDesiredSize(100)
        assertEquals 2, policy.calculateDesiredSize(101)
    }
    
    @Test
    public void testLowerBounds() {
        TestCluster tc = [1]
        policy.@entity = tc
        policy.setMetricLowerBound 100
        policy.setMetricUpperBound 10000
        assertEquals 1, policy.calculateDesiredSize(101)
        assertEquals 1, policy.calculateDesiredSize(100)
        assertEquals 0, policy.calculateDesiredSize(99)
    }
    
    @Test
    public void clustersWithSeveralEntities() {
        TestCluster tc = [3]
        policy.@entity = tc
        policy.setMetricLowerBound 50
        policy.setMetricUpperBound 100
        assertEquals 3, policy.calculateDesiredSize(99)
        assertEquals 3, policy.calculateDesiredSize(100)
        assertEquals 4, policy.calculateDesiredSize(101)
        
        assertEquals 2, policy.calculateDesiredSize(49)
        assertEquals 3, policy.calculateDesiredSize(50)
        assertEquals 3, policy.calculateDesiredSize(51)

    }
    
    @Test
    public void extremeResizes() {
        TestCluster tc = [5]
        policy.@entity = tc
        policy.setMetricLowerBound 50
        policy.setMetricUpperBound 100
        assertEquals 10, policy.calculateDesiredSize(200)
        assertEquals 0, policy.calculateDesiredSize(9)
        // Metric lower bound is 50 shared between 5 entities
        assertEquals 1, policy.calculateDesiredSize(10)
        assertEquals 1, policy.calculateDesiredSize(11)
        assertEquals 2, policy.calculateDesiredSize(20)
    }
    
    @Test
    public void obeysMinAndMaxSize() {
        TestCluster tc = [4]
        policy.@entity = tc
        policy.setMinSize 2
        policy.setMaxSize 6
        policy.setMetricLowerBound 50
        policy.setMetricUpperBound 100
        
        TestCluster tcNoResize = [4]
        ResizerPolicy policyNoResize = new ResizerPolicy(null)
        policyNoResize.@entity = tcNoResize
        policyNoResize.setMetricLowerBound 50
        policyNoResize.setMetricUpperBound 100
        
        assertEquals 2, policy.calculateDesiredSize(0)
        assertEquals 0, policyNoResize.calculateDesiredSize(0)
        
        assertEquals 6, policy.calculateDesiredSize(175)
        assertEquals 7, policyNoResize.calculateDesiredSize(175)
        
    }
    
    @InheritConstructors
    private static class TestCluster extends DynamicCluster {
        
        public int size
        
        TestCluster(int initialSize) {
            super(newEntity: {})
            size = initialSize
        }
        
        @Override
        public int getCurrentSize() {
            return size
        }
        
    }
    
    
}
