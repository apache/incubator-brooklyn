package brooklyn.entity.webapp.jboss;

import static brooklyn.entity.rebind.RebindTestUtils.serializeRebindAndManage;
import static brooklyn.test.EntityTestUtils.assertAttributeEqualsEventually;
import static brooklyn.test.HttpTestUtils.assertHttpStatusCodeEquals;
import static brooklyn.test.HttpTestUtils.assertHttpStatusCodeEventuallyEquals;
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

import brooklyn.entity.Application;
import brooklyn.entity.basic.AbstractApplication;
import brooklyn.entity.basic.SoftwareProcessEntity;
import brooklyn.entity.proxy.nginx.NginxController;
import brooklyn.entity.webapp.ControlledDynamicWebAppCluster;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.mementos.BrooklynMemento;
import brooklyn.test.WebAppMonitor;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.MutableMap;
import brooklyn.util.internal.TimeExtras;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

public class ControlledDynamicWebAppClusterRebindIntegrationTest {
    private static final Logger LOG = LoggerFactory.getLogger(ControlledDynamicWebAppClusterRebindIntegrationTest.class);
    
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
        NginxController origNginx = new NginxController(MutableMap.of("domain", "localhost"), origApp);

    	new ControlledDynamicWebAppCluster(
    			MutableMap.builder()
    					.put("factory", new JBoss7ServerFactory(MutableMap.of("war", warUrl.toString())))
    					.put("initialSize", "2")
    					.put("controller", origNginx)
    					.build(),
    			origApp);
    	
        origApp.start(ImmutableList.of(localhostProvisioningLocation));
        
        assertHttpStatusCodeEventuallyEquals(origNginx.getAttribute(JBoss7Server.ROOT_URL), 200);
        WebAppMonitor monitor = newWebAppMonitor(origNginx.getAttribute(JBoss7Server.ROOT_URL));
        
        // Rebind
        newApp = (TestApplication) serializeRebindAndManage(origApp, getClass().getClassLoader());
        NginxController newNginx = (NginxController) Iterables.find(newApp.getOwnedChildren(), Predicates.instanceOf(NginxController.class));
        ControlledDynamicWebAppCluster newCluster = (ControlledDynamicWebAppCluster) Iterables.find(newApp.getOwnedChildren(), Predicates.instanceOf(ControlledDynamicWebAppCluster.class));

        assertAttributeEqualsEventually(newNginx, SoftwareProcessEntity.SERVICE_UP, true);
        assertHttpStatusCodeEventuallyEquals(newNginx.getAttribute(JBoss7Server.ROOT_URL), 200);

        // Resize the cluster; nginx routing rule will update
        assertEquals(newCluster.getCurrentSize(), (Integer)2);
        newCluster.resize(1);
        
        Thread.sleep(1000);
        assertHttpStatusCodeEquals(newNginx.getAttribute(JBoss7Server.ROOT_URL), 200);
        
        assertEquals(monitor.getFailures(), 0);
    }
}
