package brooklyn.entity.webapp;

import static brooklyn.test.TestUtils.*
import static java.util.concurrent.TimeUnit.*
import static org.testng.AssertJUnit.*

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.basic.ApplicationBuilder
import brooklyn.entity.basic.Entities
import brooklyn.entity.proxying.BasicEntitySpec
import brooklyn.location.basic.SimulatedLocation
import brooklyn.test.entity.TestApplication
import brooklyn.test.entity.TestJavaWebAppEntity
import brooklyn.util.internal.TimeExtras

/**
 * TODO clarify test purpose
 */
public class DynamicWebAppClusterTest {
    private static final Logger log = LoggerFactory.getLogger(this)
    
    static { TimeExtras.init() }

    private TestApplication app;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        app = ApplicationBuilder.builder(TestApplication.class).manage();
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroy(app);
    }

    @Test
    public void testRequestCountAggregation() {
        DynamicWebAppCluster cluster = app.createAndManageChild(BasicEntitySpec.newInstance(DynamicWebAppCluster.class)
                .configure("initialSize", 2)
                .configure("factory", { properties -> new TestJavaWebAppEntity(properties) }));
        
        app.start([new SimulatedLocation()])
        
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
        DynamicWebAppCluster cluster = new DynamicWebAppClusterImpl(
            factory: { properties -> new TestJavaWebAppEntity(properties + ["a": 1]) },
            parent:app) {
                protected Map getCustomChildFlags() { ["c":3] }
        }
        cluster.factory.configure(b: 2);
        Entities.manage(cluster);
        
        app.start([new SimulatedLocation()])
        assertEquals 1, cluster.members.size()
        def we = cluster.members[0]
        assertEquals we.a, 1
        assertEquals we.b, 2
        assertEquals we.c, 3
    }
}
