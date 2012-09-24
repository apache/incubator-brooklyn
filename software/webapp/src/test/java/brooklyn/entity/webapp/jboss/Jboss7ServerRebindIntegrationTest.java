package brooklyn.entity.webapp.jboss;

import static brooklyn.entity.rebind.RebindTestUtils.serializeRebindAndManage;
import static brooklyn.test.TestUtils.assertAttributeEventually;
import static brooklyn.test.TestUtils.assertUrlStatusCodeEventually;
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

import brooklyn.entity.basic.SoftwareProcessEntity;
import brooklyn.entity.webapp.tomcat.TomcatServer;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.test.WebAppMonitor;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.MutableMap;
import brooklyn.util.internal.TimeExtras;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

/**
 * This tests the operation of the {@link TomcatServer} entity.
 * 
 * FIXME this test is largely superseded by WebApp*IntegrationTest which tests inter alia Tomcat
 */
public class Jboss7ServerRebindIntegrationTest {
    private static final Logger LOG = LoggerFactory.getLogger(Jboss7ServerRebindIntegrationTest.class);
    
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
    public void testRebindsToRunningServer() throws Exception {
    	// Start a jboss, and wait for it to be fully up
        JBoss7Server origServer = new JBoss7Server(MutableMap.of("war", warUrl.toString()), origApp);
        origApp.start(ImmutableList.of(localhostProvisioningLocation));
        
        assertUrlStatusCodeEventually(origServer.getAttribute(JBoss7Server.ROOT_URL), 200);
        WebAppMonitor monitor = newWebAppMonitor(origServer.getAttribute(JBoss7Server.ROOT_URL));
        
        // Rebind
        newApp = (TestApplication) serializeRebindAndManage(origApp, getClass().getClassLoader());
        JBoss7Server newServer = (JBoss7Server) Iterables.find(newApp.getOwnedChildren(), Predicates.instanceOf(JBoss7Server.class));

        assertEquals(newServer.getAttribute(JBoss7Server.ROOT_URL), origServer.getAttribute(JBoss7Server.ROOT_URL));
        assertEquals(newServer.getAttribute(JBoss7Server.MANAGEMENT_PORT), origServer.getAttribute(JBoss7Server.MANAGEMENT_PORT));
        assertEquals(newServer.getAttribute(JBoss7Server.DEPLOYED_WARS), origServer.getAttribute(JBoss7Server.DEPLOYED_WARS));
        
        assertAttributeEventually(newServer, SoftwareProcessEntity.SERVICE_UP, true);
        assertUrlStatusCodeEventually(newServer.getAttribute(JBoss7Server.ROOT_URL), 200);

        // confirm that deploy() effector affects the correct jboss server 
        newServer.deploy(warUrl.toString(), "myhello.war");
        assertUrlStatusCodeEventually(newServer.getAttribute(JBoss7Server.ROOT_URL)+"myhello", 200);
        
        assertEquals(monitor.getFailures(), 0);
    }
}
