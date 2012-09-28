package brooklyn.entity.webapp.jboss;

import static brooklyn.entity.rebind.RebindTestUtils.serializeRebindAndManage;
import static brooklyn.test.HttpTestUtils.assertHttpStatusCodeEquals;
import static brooklyn.test.HttpTestUtils.assertHttpStatusCodeEventuallyEquals;
import static brooklyn.test.HttpTestUtils.assertUrlUnreachableEventually;
import static org.testng.Assert.assertEquals;

import java.net.URL;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.Entity;
import brooklyn.entity.webapp.DynamicWebAppCluster;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.test.WebAppMonitor;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.MutableMap;
import brooklyn.util.internal.TimeExtras;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

public class DynamicWebAppClusterRebindIntegrationTest {
    private static final Logger LOG = LoggerFactory.getLogger(DynamicWebAppClusterRebindIntegrationTest.class);
    
    static { TimeExtras.init(); }

    private URL warUrl;
    private LocalhostMachineProvisioningLocation localhostProvisioningLocation;
    private TestApplication origApp;
    private TestApplication newApp;
    private List<WebAppMonitor> webAppMonitors = new CopyOnWriteArrayList<WebAppMonitor>();
	private ExecutorService executor;
    
    @BeforeMethod(groups = "Integration")
    public void setUp() {
    	String warPath = "hello-world.war";
        warUrl = getClass().getClassLoader().getResource(warPath);

    	localhostProvisioningLocation = new LocalhostMachineProvisioningLocation();
        origApp = new TestApplication();
        executor = Executors.newCachedThreadPool();
    }

    @AfterMethod(groups = "Integration", alwaysRun=true)
    public void tearDown() throws Exception {
        for (WebAppMonitor monitor : webAppMonitors) {
        	monitor.terminate();
        }
        if (executor != null) executor.shutdownNow();
        if (newApp != null) newApp.stop();
        if (origApp != null) origApp.stop();
    }

    private WebAppMonitor newWebAppMonitor(String url) {
    	WebAppMonitor monitor = new WebAppMonitor(url)
//    			.delayMillis(0)
		    	.logFailures(LOG);
    	webAppMonitors.add(monitor);
    	executor.execute(monitor);
    	return monitor;
    }
    
    @Test(groups = "Integration")
    public void testRebindsToRunningCluster() throws Exception {
        DynamicWebAppCluster origCluster = new DynamicWebAppCluster(
    			MutableMap.builder()
    					.put("factory", new JBoss7ServerFactory(MutableMap.of("war", warUrl.toString())))
    					.put("initialSize", 1)
    					.build(),
    			origApp);
    	
        origApp.start(ImmutableList.of(localhostProvisioningLocation));
        JBoss7Server origJboss = (JBoss7Server) Iterables.find(origCluster.getOwnedChildren(), Predicates.instanceOf(JBoss7Server.class));
        String jbossUrl = origJboss.getAttribute(JBoss7Server.ROOT_URL);
        
        assertHttpStatusCodeEventuallyEquals(jbossUrl, 200);
        WebAppMonitor monitor = newWebAppMonitor(jbossUrl);
        
        // Rebind
        newApp = (TestApplication) serializeRebindAndManage(origApp, getClass().getClassLoader());
        DynamicWebAppCluster newCluster = (DynamicWebAppCluster) Iterables.find(newApp.getOwnedChildren(), Predicates.instanceOf(DynamicWebAppCluster.class));

        assertHttpStatusCodeEquals(jbossUrl, 200);

        // Confirm the cluster is usable: we can scale-up
        assertEquals(newCluster.getCurrentSize(), (Integer)1);
        newCluster.resize(2);

        Iterable<Entity> newJbosses = Iterables.filter(newCluster.getOwnedChildren(), Predicates.instanceOf(JBoss7Server.class));
        assertEquals(Iterables.size(newJbosses), 2);
        for (Entity j : newJbosses) {
            assertHttpStatusCodeEventuallyEquals(j.getAttribute(JBoss7Server.ROOT_URL), 200);
        }

        // Ensure while doing all of this the original jboss server remained reachable
        assertEquals(monitor.getFailures(), 0);
        
        // Ensure cluster is usable: we can scale back to stop the original jboss server
        newCluster.resize(0);
        
        assertUrlUnreachableEventually(jbossUrl);
    }
}
