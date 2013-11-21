package brooklyn.entity.webapp.jboss;

import static brooklyn.test.TestUtils.assertAttributeEventually;
import static brooklyn.test.TestUtils.assertUrlStatusCodeEventually;
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

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.rebind.RebindTestUtils;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.test.WebAppMonitor;
import brooklyn.test.entity.TestApplication;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;

/**
 * TODO re-write this like WebAppIntegrationTest, rather than being jboss7 specific.
 */
public class Jboss7ServerRebindIntegrationTest {
    private static final Logger LOG = LoggerFactory.getLogger(Jboss7ServerRebindIntegrationTest.class);
    
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
    public void testRebindsToRunningServer() throws Exception {
    	// Start an app-server, and wait for it to be fully up
        JBoss7Server origServer = origApp.createAndManageChild(EntitySpec.create(JBoss7Server.class)
                    .configure("war", warUrl.toString()));
        
        origApp.start(ImmutableList.of(localhostProvisioningLocation));
        
        assertUrlStatusCodeEventually(origServer.getAttribute(JBoss7Server.ROOT_URL), 200);
        WebAppMonitor monitor = newWebAppMonitor(origServer.getAttribute(JBoss7Server.ROOT_URL));
        
        // Rebind
        newApp = rebind();
        JBoss7Server newServer = (JBoss7Server) Iterables.find(newApp.getChildren(), Predicates.instanceOf(JBoss7Server.class));

        assertEquals(newServer.getAttribute(JBoss7Server.ROOT_URL), origServer.getAttribute(JBoss7Server.ROOT_URL));
        assertEquals(newServer.getAttribute(JBoss7Server.MANAGEMENT_HTTP_PORT), origServer.getAttribute(JBoss7Server.MANAGEMENT_HTTP_PORT));
        assertEquals(newServer.getAttribute(JBoss7Server.DEPLOYED_WARS), origServer.getAttribute(JBoss7Server.DEPLOYED_WARS));
        
        assertAttributeEventually(newServer, SoftwareProcess.SERVICE_UP, true);
        assertUrlStatusCodeEventually(newServer.getAttribute(JBoss7Server.ROOT_URL), 200);

        // confirm that deploy() effector affects the correct jboss server 
        newServer.deploy(warUrl.toString(), "myhello.war");
        assertUrlStatusCodeEventually(newServer.getAttribute(JBoss7Server.ROOT_URL)+"myhello", 200);
        
        assertEquals(monitor.getFailures(), 0);
    }
}
