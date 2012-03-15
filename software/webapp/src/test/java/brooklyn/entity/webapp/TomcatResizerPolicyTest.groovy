package brooklyn.policy

import static brooklyn.test.TestUtils.*
import static java.util.concurrent.TimeUnit.*
import static org.testng.AssertJUnit.*

import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.LocallyManagedEntity
import brooklyn.entity.basic.EntityLocal
import brooklyn.entity.trait.Resizable
import brooklyn.entity.webapp.*
import brooklyn.entity.webapp.tomcat.*
import brooklyn.event.SensorEvent
import brooklyn.event.SensorEventListener
import brooklyn.event.basic.BasicSensorEvent
import brooklyn.location.basic.LocalhostMachineProvisioningLocation
import brooklyn.test.entity.TestApplication
import brooklyn.test.entity.TestCluster
import brooklyn.util.internal.TimeExtras

import com.google.common.collect.Iterables

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
    TestApplication app = new TestApplication()
    try {
        DynamicWebAppCluster cluster = new DynamicWebAppCluster(
            newEntity: { Map properties ->
                properties.httpPort = port++
                def tc = new TomcatServer(properties)
                tc.pollForHttpStatus = false
                tc.setConfig(TomcatServer.JMX_PORT.configKey, jmxP++)
                tc.setConfig(TomcatServer.SUGGESTED_SHUTDOWN_PORT, shutdownP++)
                tc
            },
            initialSize: 1,
            owner: app
        )
        
        ResizerPolicy policy = new ResizerPolicy(DynamicWebAppCluster.AVERAGE_REQUEST_COUNT)
        policy.setMetricLowerBound(0).setMetricUpperBound(1).setMinSize(1)
        cluster.addPolicy(policy)
        
        app.start([new LocalhostMachineProvisioningLocation(name:'london')])
        
        assertEquals 1, cluster.currentSize
        assertNotNull policy.@entity
        assertNotNull policy.@resizable
        
        TomcatServer tc = Iterables.getOnlyElement(cluster.getMembers())
        2.times { connectToURL(tc.getAttribute(TomcatServer.ROOT_URL)) }
        
        executeUntilSucceeds(timeout: 3*SECONDS) {
            assertEquals 2.0d/cluster.currentSize, cluster.getAttribute(DynamicWebAppCluster.AVERAGE_REQUEST_COUNT)
        }

        executeUntilSucceedsWithShutdown(cluster, timeout: 5*MINUTES) {
            assertTrue policy.isRunning()
            assertFalse policy.resizing.get()
            assertEquals 2, policy.calculateDesiredSize(cluster.getAttribute(DynamicWebAppCluster.AVERAGE_REQUEST_COUNT))
            assertEquals 2, cluster.currentSize
            assertEquals 1.0d, cluster.getAttribute(DynamicWebAppCluster.AVERAGE_REQUEST_COUNT)
        }
    } finally {
        app.stop()
    }
}
