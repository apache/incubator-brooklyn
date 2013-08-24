package brooklyn.entity.webapp;

import static brooklyn.test.TestUtils.*
import static java.util.concurrent.TimeUnit.*
import static org.testng.AssertJUnit.*

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.Entity
import brooklyn.entity.basic.ApplicationBuilder
import brooklyn.entity.basic.Entities
import brooklyn.entity.proxying.EntitySpec
import brooklyn.entity.trait.Startable
import brooklyn.location.basic.SimulatedLocation
import brooklyn.test.EntityTestUtils
import brooklyn.test.entity.TestApplication
import brooklyn.test.entity.TestJavaWebAppEntity
import brooklyn.util.collections.MutableMap
import brooklyn.util.internal.TimeExtras

import com.google.common.collect.Iterables

/**
 * TODO clarify test purpose
 */
public class DynamicWebAppClusterTest {
    private static final Logger log = LoggerFactory.getLogger(this)
    
    private static final int TIMEOUT_MS = 1*1000;
    private static final int SHORT_WAIT_MS = 250;
    
    static { TimeExtras.init() }

    private TestApplication app;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }

    @Test
    public void testTestJWAEntity() {
        Entity test = app.createAndManageChild(EntitySpec.create(Entity.class, TestJavaWebAppEntity.class))
        try {
            test.invoke(Startable.START, [locations:[]]).get()
        } catch (Throwable t) {
            t.printStackTrace();
            throw t;
        }
//        ((Startable)test).start([])
    }
    
    @Test
    public void testRequestCountAggregation() {
        DynamicWebAppCluster cluster = app.createAndManageChild(EntitySpec.create(DynamicWebAppCluster.class)
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
    
    @Test
    public void testSetsServiceUpIfMemberIsUp() throws Exception {
        DynamicWebAppCluster cluster = app.createAndManageChild(EntitySpec.create(DynamicWebAppCluster.class)
            .configure("initialSize", 1)
            .configure("factory", { properties -> new TestJavaWebAppEntity(properties) }));
    
        app.start([new SimulatedLocation()])
        
        // Should initially be false (when child has no service_up value) 
        EntityTestUtils.assertAttributeEqualsEventually(MutableMap.of("timeout", TIMEOUT_MS), cluster, DynamicWebAppCluster.SERVICE_UP, false);
        
        // When child is !service_up, should continue to report false
        Iterables.get(cluster.getChildren(), 0).setAttribute(Startable.SERVICE_UP, false);
        EntityTestUtils.assertAttributeEqualsContinually(MutableMap.of("timeout", SHORT_WAIT_MS), cluster, DynamicWebAppCluster.SERVICE_UP, false);
        
        cluster.resize(2);
        
        // When one of the two children is service_up, should report true
        Iterables.get(cluster.getChildren(), 0).setAttribute(Startable.SERVICE_UP, true);
        EntityTestUtils.assertAttributeEqualsEventually(MutableMap.of("timeout", TIMEOUT_MS), cluster, DynamicWebAppCluster.SERVICE_UP, true);

        // And if that serviceUp child goes away, should again report false
        Entities.unmanage(Iterables.get(cluster.getChildren(), 0));
        EntityTestUtils.assertAttributeEqualsEventually(MutableMap.of("timeout", TIMEOUT_MS), cluster, DynamicWebAppCluster.SERVICE_UP, false);
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
