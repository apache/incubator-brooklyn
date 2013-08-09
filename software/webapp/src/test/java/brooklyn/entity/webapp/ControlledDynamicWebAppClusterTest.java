package brooklyn.entity.webapp;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import java.net.URL;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.BasicConfigurableEntityFactory;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxy.AbstractController;
import brooklyn.entity.proxy.nginx.NginxController;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.webapp.jboss.JBoss7ServerFactory;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.test.EntityTestUtils;
import brooklyn.test.HttpTestUtils;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestJavaWebAppEntity;
import brooklyn.util.collections.MutableMap;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

/**
 * TODO clarify test purpose
 */
public class ControlledDynamicWebAppClusterTest {
    private static final Logger log = LoggerFactory.getLogger(ControlledDynamicWebAppClusterTest.class);

    private static final int TIMEOUT_MS = 10*1000;
    
    private URL warUrl;
    private TestApplication app;
    private LocalhostMachineProvisioningLocation loc;
    private List<LocalhostMachineProvisioningLocation> locs;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        String warPath = "hello-world.war";
        warUrl = getClass().getClassLoader().getResource(warPath);
        
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        loc = new LocalhostMachineProvisioningLocation();
        locs = ImmutableList.of(loc);
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }
    
    @Test(groups="Integration")
    public void testUsesCustomController() {
        NginxController controller = app.createAndManageChild(EntitySpec.create(NginxController.class).displayName("mycustom"));
        ControlledDynamicWebAppCluster cluster = app.createAndManageChild(EntitySpec.create(ControlledDynamicWebAppCluster.class)
                .configure("initialSize", 0)
                .configure(ControlledDynamicWebAppCluster.CONTROLLER, controller)
                .configure("factory", new JBoss7ServerFactory(MutableMap.of("war", warUrl.toString()))));
        app.start(locs);

        EntityTestUtils.assertAttributeEqualsEventually(controller, NginxController.SERVICE_UP, true);
        assertEquals(cluster.getController(), controller);

        // Stopping cluster should not stop controller (because it didn't create it)
        cluster.stop();
        EntityTestUtils.assertAttributeEquals(controller, NginxController.SERVICE_UP, true);
    }
    
    @Test(groups="Integration")
    public void testUsesCustomControllerSpec() {
        EntitySpec<NginxController> controllerSpec = EntitySpec.create(NginxController.class).displayName("mycustom");
        ControlledDynamicWebAppCluster cluster = app.createAndManageChild(EntitySpec.create(ControlledDynamicWebAppCluster.class)
                .configure("initialSize", 0)
                .configure(ControlledDynamicWebAppCluster.CONTROLLER_SPEC, controllerSpec)
                .configure("factory", new JBoss7ServerFactory(MutableMap.of("war", warUrl.toString()))));
        app.start(locs);
        AbstractController controller = cluster.getController();
        
        EntityTestUtils.assertAttributeEqualsEventually(controller, NginxController.SERVICE_UP, true);
        assertEquals(controller.getDisplayName(), "mycustom");

        // Stopping cluster should stop the controller (because it created it)
        cluster.stop();
        EntityTestUtils.assertAttributeEquals(controller, NginxController.SERVICE_UP, false);
    }
    
    @Test(groups="Integration")
    public void testConfiguresController() {
        ControlledDynamicWebAppCluster cluster = app.createAndManageChild(EntitySpec.create(ControlledDynamicWebAppCluster.class)
                .configure("initialSize", 1)
                .configure("factory", new JBoss7ServerFactory(MutableMap.of("war", warUrl.toString()))));
        app.start(locs);

        String url = cluster.getController().getAttribute(NginxController.ROOT_URL);
        HttpTestUtils.assertHttpStatusCodeEventuallyEquals(url, 200);
        HttpTestUtils.assertContentEventuallyContainsText(url, "Hello");
    }
    
    // Needs to be integration test because still using nginx controller; could pass in mock controller
    @Test(groups="Integration")
    public void testSetsInitialSize() {
        ControlledDynamicWebAppCluster cluster = app.createAndManageChild(EntitySpec.create(ControlledDynamicWebAppCluster.class)
                .configure("initialSize", 2)
                .configure("factory", new BasicConfigurableEntityFactory<TestJavaWebAppEntity>(TestJavaWebAppEntity.class)));
        app.start(locs);

        Iterable<TestJavaWebAppEntity> webservers = Iterables.filter(cluster.getCluster().getMembers(), TestJavaWebAppEntity.class);
        assertEquals(Iterables.size(webservers), 2, "webservers="+webservers);
    }
    
    @Test(groups="Integration")
    public void testSetsToplevelHostnameFromController() {
        ControlledDynamicWebAppCluster cluster = app.createAndManageChild(EntitySpec.create(ControlledDynamicWebAppCluster.class)
                .configure("initialSize", 1)
                .configure("factory", new JBoss7ServerFactory(MutableMap.of("war", warUrl.toString()))));
        app.start(locs);

        String expectedHostname = cluster.getController().getAttribute(NginxController.HOSTNAME);
        String expectedRootUrl = cluster.getController().getAttribute(NginxController.ROOT_URL);
        boolean expectedServiceUp = true;
        
        assertNotNull(expectedHostname);
        assertNotNull(expectedRootUrl);
        
        EntityTestUtils.assertAttributeEqualsEventually(MutableMap.of("timeout", TIMEOUT_MS), cluster, ControlledDynamicWebAppCluster.HOSTNAME, expectedHostname);
        EntityTestUtils.assertAttributeEqualsEventually(MutableMap.of("timeout", TIMEOUT_MS), cluster, ControlledDynamicWebAppCluster.ROOT_URL, expectedRootUrl);
        EntityTestUtils.assertAttributeEqualsEventually(MutableMap.of("timeout", TIMEOUT_MS), cluster, ControlledDynamicWebAppCluster.SERVICE_UP, expectedServiceUp);
    }
    
}
