package brooklyn.entity.group;

import static brooklyn.test.TestUtils.*
import static java.util.concurrent.TimeUnit.*
import static org.testng.AssertJUnit.*

import org.testng.annotations.Test

import brooklyn.entity.Application
import brooklyn.entity.webapp.DynamicWebAppCluster
import brooklyn.location.basic.SimulatedLocation
import brooklyn.test.entity.TestApplication
import brooklyn.test.entity.TestJavaWebAppEntity
import brooklyn.util.internal.TimeExtras

/**
 * TODO clarify test purpose
 */
public class DynamicWebAppClusterTest {
    static { TimeExtras.init() }
    
    @Test
    public void testRequestCountAggregation() {
        Application app = new TestApplication()
        DynamicWebAppCluster cluster = new DynamicWebAppCluster(
            initialSize: 2,
            newEntity: { properties -> new TestJavaWebAppEntity(properties) },
            owner:app)
        cluster.start([new SimulatedLocation()])
        
        cluster.members.each { it.spoofRequest() }
        executeUntilSucceeds(timeout: 3*SECONDS) {
            assertEquals 2, cluster.getAttribute(DynamicWebAppCluster.TOTAL_REQUEST_COUNT)
        }
        
        cluster.members.each { it.spoofRequest(); it.spoofRequest() }
        executeUntilSucceeds(timeout: 3*SECONDS) {
            assertEquals 3d, cluster.getAttribute(DynamicWebAppCluster.AVERAGE_REQUEST_COUNT)
        }
    }
}
