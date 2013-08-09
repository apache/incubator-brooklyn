package brooklyn.entity.webapp;

import static brooklyn.test.HttpTestUtils.connectToUrl;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.webapp.tomcat.TomcatServer;
import brooklyn.entity.webapp.tomcat.TomcatServerImpl;
import brooklyn.location.PortRange;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.location.basic.PortRanges;
import brooklyn.policy.autoscaling.AutoScalerPolicy;
import brooklyn.test.Asserts;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.collections.MutableMap;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

public class TomcatAutoScalerPolicyTest {
    
    // TODO Test is time-sensitive: we send two web-requests in rapid succession, and expect the average workrate to
    // be 2 msgs/sec; we then expect resizing to kick-in.
    // P speculate that... if for some reason things are running slow (e.g. GC during that one second), then brooklyn 
    // may not report the 2 msgs/sec.

    private LocalhostMachineProvisioningLocation loc;
    private TestApplication app;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        loc = new LocalhostMachineProvisioningLocation(MutableMap.of("name", "london"));
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }
    
    @Test(groups="Integration")
    public void testWithTomcatServers() throws Exception {
        /*
         * One DynamicWebAppClster with auto-scaler policy
         * AutoScaler listening to DynamicWebAppCluster.TOTAL_REQS
         * AutoScaler minSize 1
         * AutoScaler upper metric 1
         * AutoScaler lower metric 0
         * .. send one request
         * wait til auto-scaling complete
         * assert cluster size 2
         */
        
        PortRange httpPort = PortRanges.fromString("7880+");
        PortRange jmxP = PortRanges.fromString("32199+");
        PortRange shutdownP = PortRanges.fromString("31880+");
        
        final DynamicWebAppCluster cluster = app.createAndManageChild(EntitySpec.create(DynamicWebAppCluster.class)
                .configure(DynamicWebAppCluster.INITIAL_SIZE, 1)
                .configure(DynamicWebAppCluster.MEMBER_SPEC, EntitySpec.create(TomcatServer.class)
                        .configure(TomcatServer.HTTP_PORT.getConfigKey(), httpPort)
                        .configure(TomcatServer.JMX_PORT.getConfigKey(), jmxP)
                        .configure(TomcatServer.SHUTDOWN_PORT, shutdownP)));

        final AutoScalerPolicy policy = AutoScalerPolicy.builder()
                .metric(DynamicWebAppCluster.REQUEST_COUNT_PER_NODE)
                .metricRange(0, 1)
                .minPoolSize(1)
                .build();
        cluster.addPolicy(policy);
        
        app.start(ImmutableList.of(loc));
        
        assertEquals((Integer)1, cluster.getCurrentSize());
        
        TomcatServer tc = (TomcatServer) Iterables.getOnlyElement(cluster.getMembers());
        for (int i = 0; i < 2; i++) {
            connectToUrl(tc.getAttribute(TomcatServerImpl.ROOT_URL));
        }
        
        Asserts.succeedsEventually(MutableMap.of("timeout", 3000), new Runnable() {
            public void run() {
                assertEquals(2.0d/cluster.getCurrentSize(), cluster.getAttribute(DynamicWebAppCluster.AVERAGE_REQUEST_COUNT));
            }});

        Asserts.succeedsEventually(MutableMap.of("timeout", 5*60*1000), new Runnable() {
            public void run() {
                assertTrue(policy.isRunning());
                assertEquals((Integer)2, cluster.getCurrentSize());
                assertEquals(1.0d, cluster.getAttribute(DynamicWebAppCluster.AVERAGE_REQUEST_COUNT));
            }});
    }
}
