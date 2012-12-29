package brooklyn.entity.webapp;

import static brooklyn.test.TestUtils.*
import static java.util.concurrent.TimeUnit.*
import static org.testng.AssertJUnit.*

import java.util.Map

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.Test

import brooklyn.entity.Application
import brooklyn.location.basic.SimulatedLocation
import brooklyn.test.entity.TestApplication
import brooklyn.test.entity.TestJavaWebAppEntity
import brooklyn.util.ResourceUtils;
import brooklyn.util.internal.TimeExtras

/**
 * TODO clarify test purpose
 */
public class DynamicWebAppClusterTest {
    private static final Logger log = LoggerFactory.getLogger(this)
    
    static { TimeExtras.init() }
    
    @Test
    public void testRequestCountAggregation() {
        TestApplication app = new TestApplication()
        DynamicWebAppCluster cluster = new DynamicWebAppCluster(
            initialSize: 2,
            factory: { properties -> new TestJavaWebAppEntity(properties) },
            parent:app)
        app.startManagement();
        cluster.start([new SimulatedLocation()])
        
        cluster.members.each { it.spoofRequest() }
        executeUntilSucceeds(timeout: 3*SECONDS) {
            // intermittent failure observed 4 may 2012
            assertEquals 2, cluster.getAttribute(DynamicWebAppCluster.TOTAL_REQUEST_COUNT)
        }
        
        cluster.members.each { it.spoofRequest(); it.spoofRequest() }
        executeUntilSucceeds(timeout: 3*SECONDS) {
            assertEquals 3d, cluster.getAttribute(DynamicWebAppCluster.AVERAGE_REQUEST_COUNT)
        }
    }
    
    // FIXME Fails because of the config-closure stuff; it now coerces closure every time 
    // on entity.getConfig(key), rather than only once. So the call to cluster.factory.configure
    // updated a different instance from that retrieved subsequently!
    @Test(groups="WIP")
    public void testPropertiesToChildren() {
        TestApplication app = new TestApplication()
        DynamicWebAppCluster cluster = new DynamicWebAppCluster(
            factory: { properties -> new TestJavaWebAppEntity(properties + ["a": 1]) },
            parent:app) {
                protected Map getCustomChildFlags() { ["c":3] }
        }
        cluster.factory.configure(b: 2);
        app.startManagement();
        
        cluster.start([new SimulatedLocation()])
        assertEquals 1, cluster.members.size()
        def we = cluster.members[0]
        assertEquals we.a, 1
        assertEquals we.b, 2
        assertEquals we.c, 3
    }
}
