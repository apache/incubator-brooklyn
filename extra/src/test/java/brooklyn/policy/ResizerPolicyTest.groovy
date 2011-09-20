package brooklyn.policy

import static brooklyn.test.TestUtils.*
import static java.util.concurrent.TimeUnit.*
import static org.testng.AssertJUnit.*

import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.webapp.DynamicWebAppCluster
import brooklyn.entity.webapp.tomcat.TomcatServer
import brooklyn.event.basic.BasicSensorEvent
import brooklyn.location.basic.LocalhostMachineProvisioningLocation
import brooklyn.test.entity.TestApplication
import brooklyn.test.entity.TestCluster
import brooklyn.util.internal.TimeExtras

import com.google.common.collect.Iterables

class ResizerPolicyTest {

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
    
    @Test(groups=["Integration"])
    public void testWithTomcatServers() {
        /**
         * One DynamicWebAppClster with resizer policy
         * Resizer listening to DynamicWebAppCluster.TOTAL_REQS
         * Resizer minSize 1
         * Resizer upper metric 1
         * Resizer lower metric 0
         * .. send one request
         * wait til ResizerLock released
         * assert cluster size 2 
         */
        
        Integer port = 7880
        Integer jmxP = 32199
        Integer shutdownP = 31880
        DynamicWebAppCluster cluster = new DynamicWebAppCluster(
            newEntity: { Map properties ->
                properties.httpPort = port++
                def tc = new TomcatServer(properties)
                tc.pollForHttpStatus = false
                tc.setConfig(TomcatServer.SUGGESTED_JMX_PORT, jmxP++)
                tc.setConfig(TomcatServer.SUGGESTED_SHUTDOWN_PORT, shutdownP++)
                tc
            },
            initialSize: 1,
            owner: new TestApplication()
        )
        
        ResizerPolicy policy = new ResizerPolicy(DynamicWebAppCluster.AVERAGE_REQUEST_COUNT)
        policy.setMetricLowerBound(0).setMetricUpperBound(1).setMinSize(1)
        cluster.addPolicy(policy)
        
        cluster.start([new LocalhostMachineProvisioningLocation(name:'london', count:4)])
        assertEquals 1, cluster.currentSize
        assertNotNull policy.@entity
        assertNotNull policy.@resizable
        
        TomcatServer tc = Iterables.getOnlyElement(cluster.getMembers())
        2.times { connectToURL(tc.getAttribute(TomcatServer.ROOT_URL)) }
        
        try {
            executeUntilSucceeds(timeout: 3*SECONDS, {
                assertEquals 2.0d/cluster.currentSize, cluster.getAttribute(DynamicWebAppCluster.AVERAGE_REQUEST_COUNT)
            })

            executeUntilSucceeds(timeout: 10*SECONDS, {
                assertTrue policy.isRunning()
                assertFalse policy.resizing.get()
                assertEquals 2, policy.calculateDesiredSize(cluster.getAttribute(DynamicWebAppCluster.AVERAGE_REQUEST_COUNT))
                assertEquals 2, cluster.currentSize
                assertEquals 1.0d, cluster.getAttribute(DynamicWebAppCluster.AVERAGE_REQUEST_COUNT)
            })
        } finally {
            cluster.stop()
        }
    }
}
