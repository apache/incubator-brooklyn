package brooklyn.entity.webapp;

import static org.testng.Assert.assertEquals;

import java.net.URL;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.BasicConfigurableEntityFactory;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxy.nginx.NginxController;
import brooklyn.entity.webapp.jboss.JBoss7ServerFactory;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.test.HttpTestUtils;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestJavaWebAppEntity;
import brooklyn.util.MutableMap;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

/**
 * TODO clarify test purpose
 */
public class ControlledDynamicWebAppClusterTest {
    private static final Logger log = LoggerFactory.getLogger(ControlledDynamicWebAppClusterTest.class);
    
    private URL warUrl;
    private TestApplication app;
    private LocalhostMachineProvisioningLocation loc;
    private List<LocalhostMachineProvisioningLocation> locs;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        String warPath = "hello-world.war";
        warUrl = getClass().getClassLoader().getResource(warPath);
        
        app = new TestApplication();
        loc = new LocalhostMachineProvisioningLocation();
        locs = ImmutableList.of(loc);
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroy(app);
    }
    
    @Test(groups="Integration")
    public void testConfiguresController() {
        ControlledDynamicWebAppCluster cluster = new ControlledDynamicWebAppCluster(
            MutableMap.builder()
                    .put("initialSize", 1)
                    .put("factory", new JBoss7ServerFactory(MutableMap.of("war", warUrl.toString())))
                    .put("parent", app)
                    .build());
        Entities.startManagement(app);
        app.start(locs);

        String url = cluster.getController().getAttribute(NginxController.ROOT_URL);
        HttpTestUtils.assertHttpStatusCodeEventuallyEquals(url, 200);
        HttpTestUtils.assertContentEventuallyContainsText(url, "Hello");
    }
    
    // Needs to be integration test because still using nginx controller; could pass in mock controller
    @Test(groups="Integration")
    public void testSetsInitialSize() {
        ControlledDynamicWebAppCluster cluster = new ControlledDynamicWebAppCluster(
            MutableMap.builder()
                    .put("initialSize", 2)
                    .put("factory", new BasicConfigurableEntityFactory<TestJavaWebAppEntity>(TestJavaWebAppEntity.class))
                    .put("parent", app)
                    .build());
        Entities.startManagement(app);
        app.start(locs);

        Iterable<TestJavaWebAppEntity> webservers = Iterables.filter(cluster.getCluster().getMembers(), TestJavaWebAppEntity.class);
        assertEquals(Iterables.size(webservers), 2, "webservers="+webservers);
    }
}
