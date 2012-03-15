package brooklyn.policy

import static brooklyn.test.TestUtils.*
import static java.util.concurrent.TimeUnit.*
import static org.testng.AssertJUnit.*

import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.LocallyManagedEntity
import brooklyn.entity.basic.EntityLocal
import brooklyn.entity.trait.Resizable
import brooklyn.event.SensorEvent
import brooklyn.event.SensorEventListener
import brooklyn.event.basic.BasicSensorEvent
import brooklyn.location.basic.LocalhostMachineProvisioningLocation
import brooklyn.test.entity.TestApplication
import brooklyn.test.entity.TestCluster
import brooklyn.util.internal.TimeExtras

import com.google.common.collect.Iterables

class ResizerPolicyTest {
    
    /**
     * Test class for providing a Resizable LocallyManagedEntity for policy testing
     * It is hooked up to a TestCluster that can be used to make assertions against
     */
    public static class LocallyResizableEntity extends LocallyManagedEntity implements Resizable {
        TestCluster tc
        public LocallyResizableEntity (TestCluster tc) { this.tc = tc }
        Integer resize(Integer newSize) { tc.size = newSize }
        Integer getCurrentSize() { return tc.size }
    }

    static { TimeExtras.init() }
    
    ResizerPolicy policy
    TestCluster tc
    
    @BeforeMethod()
    public void before() {
        policy = new ResizerPolicy<Integer>(null)
        tc = policy.@resizable = new TestCluster(1)
        policy.setMinSize 0
    }
    
    @Test
    public void testUpperBounds() {
        tc.size = 1
        policy.setMetricLowerBound 0
        policy.setMetricUpperBound 100
        assertEquals 1, policy.calculateDesiredSize(99)
        assertEquals 1, policy.calculateDesiredSize(100)
        assertEquals 2, policy.calculateDesiredSize(101)
    }
    
    @Test
    public void testLowerBounds() {
        tc.size = 1
        policy.@resizable = tc
        policy.setMetricLowerBound 100
        policy.setMetricUpperBound 10000
        assertEquals 1, policy.calculateDesiredSize(101)
        assertEquals 1, policy.calculateDesiredSize(100)
        assertEquals 0, policy.calculateDesiredSize(99)
    }
    
    @Test
    public void clustersWithSeveralEntities() {
        tc.size = 3
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
        tc.size = 5
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
        tc.size = 4
        policy.setMinSize 2
        policy.setMaxSize 6
        policy.setMetricLowerBound 50
        policy.setMetricUpperBound 100
        
        TestCluster tcNoResize = [4]
        ResizerPolicy policyNoResize = new ResizerPolicy(null)
        policyNoResize.@resizable = tcNoResize
        policyNoResize.setMetricLowerBound 50
        policyNoResize.setMetricUpperBound 100
        
        assertEquals 2, policy.calculateDesiredSize(0)
        assertEquals 0, policyNoResize.calculateDesiredSize(0)
        
        assertEquals 6, policy.calculateDesiredSize(175)
        assertEquals 7, policyNoResize.calculateDesiredSize(175)
    }
    
    @Test
    public void testDestructionState() {
        policy.destroy()
        assertEquals true, policy.isDestroyed()
        assertEquals false, policy.isRunning()
        assertEquals 0, policy.getAllSubscriptions().size()
    }
    
    @Test
    public void testPostDestructionActions() {
        policy.destroy()
        policy.onEvent(new BasicSensorEvent<Integer>(null, null, null) {
                Integer getValue() {
                    throw new IllegalStateException("Should not be called when destroyed")
                }
            }
        )
    }
    
    @Test
    public void testSuspendState() {
        policy.suspend()
        assertEquals false, policy.isDestroyed()
        assertEquals false, policy.isRunning()
        
        policy.resume()
        assertEquals false, policy.isDestroyed()
        assertEquals true, policy.isRunning()
    }

    @Test
    public void testPostSuspendActions() {
        policy.@resizable = new TestCluster(1) {
                    Integer resize(Integer newSize) {
                        fail "Should not be resizing when suspended"
                    }
                }
        policy.setMetricLowerBound 0
        policy.setMetricUpperBound 1

        policy.suspend()
        policy.onEvent(new BasicSensorEvent<Integer>(null, null, null) {
                    Integer getValue() {
                        return 2
                    }
                })
    }
    
    @Test
    public void testPostResumeActions() {
        policy.setEntity(new LocallyResizableEntity(tc))
        
        policy.setMetricLowerBound 0
        policy.setMetricUpperBound 1
        
        assertEquals 2, policy.calculateDesiredSize(2)

        policy.suspend()
        policy.resume()
        policy.onEvent(new BasicSensorEvent<Integer>(null, null, null) {
                    Integer getValue() {
                        return 2
                    }
                })
        
        executeUntilSucceeds(timeout: 3*SECONDS) {
            assertEquals 2, tc.size
        }
    }

    @Test
    public void testDestructionUnsubscribes() {
        EntityLocal entity = new LocallyResizableEntity(null)
        policy.setEntity(entity)
        policy.subscribe(entity, null, new SensorEventListener<?>(){void onEvent(SensorEvent e) {}})
        policy.destroy()
        
        executeUntilSucceeds(timeout: 3*SECONDS) {
            assertEquals 0, policy.getAllSubscriptions().size()
        }
    }
    
}
