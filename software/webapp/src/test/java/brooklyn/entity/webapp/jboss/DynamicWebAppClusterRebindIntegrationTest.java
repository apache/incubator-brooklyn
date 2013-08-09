package brooklyn.entity.webapp.jboss;

import static brooklyn.test.HttpTestUtils.assertHttpStatusCodeEquals;
import static brooklyn.test.HttpTestUtils.assertHttpStatusCodeEventuallyEquals;
import static brooklyn.test.HttpTestUtils.assertUrlUnreachableEventually;
import static org.testng.Assert.assertEquals;

import java.io.File;
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
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.rebind.RebindTestUtils;
import brooklyn.entity.webapp.DynamicWebAppCluster;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.test.WebAppMonitor;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.collections.MutableMap;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;

public class DynamicWebAppClusterRebindIntegrationTest {
    private static final Logger LOG = LoggerFactory.getLogger(DynamicWebAppClusterRebindIntegrationTest.class);
    
    private URL warUrl;
    private LocalhostMachineProvisioningLocation localhostProvisioningLocation;
    private TestApplication origApp;
    private TestApplication newApp;
    private List<WebAppMonitor> webAppMonitors = new CopyOnWriteArrayList<WebAppMonitor>();
	private ExecutorService executor;
    
    private ClassLoader classLoader = getClass().getClassLoader();
    private LocalManagementContext origManagementContext;
    private File mementoDir;
    
    @BeforeMethod(groups = "Integration")
    public void setUp() {
    	String warPath = "hello-world.war";
        warUrl = getClass().getClassLoader().getResource(warPath);
        
        executor = Executors.newCachedThreadPool();

        mementoDir = Files.createTempDir();
        origManagementContext = RebindTestUtils.newPersistingManagementContext(mementoDir, classLoader);

    	localhostProvisioningLocation = new LocalhostMachineProvisioningLocation();
    	origApp = ApplicationBuilder.newManagedApp(TestApplication.class, origManagementContext);
    }

    @AfterMethod(groups = "Integration", alwaysRun=true)
    public void tearDown() throws Exception {
        for (WebAppMonitor monitor : webAppMonitors) {
        	monitor.terminate();
        }
        if (executor != null) executor.shutdownNow();
        if (newApp != null) Entities.destroyAll(newApp);
        if (origApp != null) Entities.destroyAll(origApp);
        if (mementoDir != null) RebindTestUtils.deleteMementoDir(mementoDir);
    }

    private TestApplication rebind() throws Exception {
        RebindTestUtils.waitForPersisted(origApp);
        
        // Stop the old management context, so original nginx won't interfere
        origManagementContext.terminate();
        
        return (TestApplication) RebindTestUtils.rebind(mementoDir, getClass().getClassLoader());
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
        DynamicWebAppCluster origCluster = origApp.createAndManageChild(EntitySpec.create(DynamicWebAppCluster.class)
                .configure("factory", new JBoss7ServerFactory(MutableMap.of("war", warUrl.toString())))
				.configure("initialSize", 1));
    	
        origApp.start(ImmutableList.of(localhostProvisioningLocation));
        JBoss7Server origJboss = (JBoss7Server) Iterables.find(origCluster.getChildren(), Predicates.instanceOf(JBoss7Server.class));
        String jbossUrl = origJboss.getAttribute(JBoss7Server.ROOT_URL);
        
        assertHttpStatusCodeEventuallyEquals(jbossUrl, 200);
        WebAppMonitor monitor = newWebAppMonitor(jbossUrl);
        
        // Rebind
        newApp = rebind();
        DynamicWebAppCluster newCluster = (DynamicWebAppCluster) Iterables.find(newApp.getChildren(), Predicates.instanceOf(DynamicWebAppCluster.class));

        assertHttpStatusCodeEquals(jbossUrl, 200);

        // Confirm the cluster is usable: we can scale-up
        assertEquals(newCluster.getCurrentSize(), (Integer)1);
        newCluster.resize(2);

        Iterable<Entity> newJbosses = Iterables.filter(newCluster.getChildren(), Predicates.instanceOf(JBoss7Server.class));
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
