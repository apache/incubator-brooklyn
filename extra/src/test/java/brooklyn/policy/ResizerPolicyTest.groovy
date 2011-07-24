package brooklyn.policy

import static brooklyn.test.TestUtils.*
import static java.util.concurrent.TimeUnit.*
import static org.testng.AssertJUnit.*

import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.webapp.DynamicWebAppCluster
import brooklyn.entity.webapp.tomcat.TomcatServer
import brooklyn.location.basic.LocalhostMachineProvisioningLocation
import brooklyn.test.entity.TestApplication
import brooklyn.test.entity.TestCluster
import brooklyn.util.internal.TimeExtras

class ResizerPolicyTest {

    static { TimeExtras.init() }
    
    ResizerPolicy policy
    
    @BeforeMethod()
    public void before() {
        policy = new ResizerPolicy(null)
        policy.setMinSize 0
    }
    
    @Test
    public void testUpperBounds() {
        TestCluster tc = [1]
        policy.@dynamicCluster = tc
        policy.setMetricLowerBound 0
        policy.setMetricUpperBound 100
        assertEquals 1, policy.calculateDesiredSize(99)
        assertEquals 1, policy.calculateDesiredSize(100)
        assertEquals 2, policy.calculateDesiredSize(101)
    }
    
    @Test
    public void testLowerBounds() {
        TestCluster tc = [1]
        policy.@dynamicCluster = tc
        policy.setMetricLowerBound 100
        policy.setMetricUpperBound 10000
        assertEquals 1, policy.calculateDesiredSize(101)
        assertEquals 1, policy.calculateDesiredSize(100)
        assertEquals 0, policy.calculateDesiredSize(99)
    }
    
    @Test
    public void clustersWithSeveralEntities() {
        TestCluster tc = [3]
        policy.@dynamicCluster = tc
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
        policy.@dynamicCluster = tc
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
        policy.@dynamicCluster = tc
        policy.setMinSize 2
        policy.setMaxSize 6
        policy.setMetricLowerBound 50
        policy.setMetricUpperBound 100
        
        TestCluster tcNoResize = [4]
        ResizerPolicy policyNoResize = new ResizerPolicy(null)
        policyNoResize.@dynamicCluster = tcNoResize
        policyNoResize.setMetricLowerBound 50
        policyNoResize.setMetricUpperBound 100
        
        assertEquals 2, policy.calculateDesiredSize(0)
        assertEquals 0, policyNoResize.calculateDesiredSize(0)
        
        assertEquals 6, policy.calculateDesiredSize(175)
        assertEquals 7, policyNoResize.calculateDesiredSize(175)
    }
    
    // what is this i don't even
    @Test(enabled = false)
    public void multipleThreadsSettingDesiredSize() {
        /* t1: set t */
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
        
        Integer port = 9000
        Integer jmxP = 32199
        Integer shutdownP = 31880
        DynamicWebAppCluster cluster = new DynamicWebAppCluster(
            newEntity: { properties ->
                properties.httpPort = port++
                def tc = new TomcatServer(properties)
                tc.setConfig(TomcatServer.SUGGESTED_JMX_PORT, jmxP++)
                tc.setConfig(TomcatServer.SUGGESTED_SHUTDOWN_PORT, shutdownP++)
                tc
            },
            initialSize: 1,
            owner: new TestApplication()
        )
        
        ResizerPolicy p = new ResizerPolicy(DynamicWebAppCluster.TOTAL_REQUEST_COUNT)
        p.setMetricLowerBound(0).setMetricUpperBound(1).setMinSize(1)
        cluster.addPolicy(p)
        
        cluster.start([new LocalhostMachineProvisioningLocation(name:'london', count:2)])
        assertEquals 1, cluster.currentSize
        
        TomcatServer tc = cluster.getMembers().toArray()[0]
        2.times { connectToURL(tc.getAttribute(TomcatServer.ROOT_URL)) }
        
        executeUntilSucceeds(timeout: 3*SECONDS, {
            assertEquals 2, cluster.getAttribute(DynamicWebAppCluster.TOTAL_REQUEST_COUNT)
        })
        
        executeUntilSucceedsWithShutdown(cluster, {
            if (!p.resizeLock.isLocked()) {
                println cluster.currentSize
                assertEquals 2, cluster.currentSize
                return true
            }
            println "locked, should be looping"
        }, timeout: 10*SECONDS)
    }
}
