package brooklyn.entity.proxy.nginx;

import static org.testng.Assert.assertEquals;

import java.net.URL;

import javax.annotation.Nullable;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.BasicConfigurableEntityFactory;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityFactory;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.proxy.StubAppServer;
import brooklyn.entity.proxying.EntitySpecs;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.test.TestUtils;
import brooklyn.test.entity.TestApplication;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

public class NginxLightIntegrationTest {

    private TestApplication app;
    private NginxController nginx;
    private DynamicCluster cluster;

    private URL war;
    private static String WAR_URL = "classpath://hello-world.war";
    
    @BeforeMethod
    public void setup() {
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
    }

    @AfterMethod(alwaysRun=true)
    public void shutdown() {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }

    // FIXME Fails because getting addEntity callback for group members while nginx is still starting,
    // so important nginx fields are still null. Therefore get NPE for cluster members, and thus targets
    // is of size zero.
    @Test(groups = {"Integration", "WIP"})
    public void testNginxTargetsMatchesClusterMembers() {
        EntityFactory<StubAppServer> serverFactory = new BasicConfigurableEntityFactory<StubAppServer>(StubAppServer.class);
        final DynamicCluster cluster = app.createAndManageChild(EntitySpecs.spec(DynamicCluster.class)
                .configure("initialSize", 2)
                .configure("factory", serverFactory));
                
        nginx = app.createAndManageChild(EntitySpecs.spec(NginxController.class)
                .configure("serverPool", cluster)
                .configure("domain", "localhost"));
        
        app.start(ImmutableList.of(new LocalhostMachineProvisioningLocation()));
        
        // Wait for url-mapping to update its TARGET_ADDRESSES (through async subscription)
        TestUtils.executeUntilSucceeds(new Runnable() {
            public void run() {
                Iterable<String> expectedTargets = Iterables.transform(cluster.getMembers(), new Function<Entity,String>() {
                        @Override public String apply(@Nullable Entity input) {
                            return input.getAttribute(Attributes.HOSTNAME)+":"+input.getAttribute(Attributes.HTTP_PORT);
                        }});
                
                assertEquals(nginx.getAttribute(NginxController.TARGETS).size(), 2);
                assertEquals(ImmutableSet.copyOf(nginx.getAttribute(NginxController.TARGETS)), ImmutableSet.copyOf(expectedTargets));
            }});
    }
}
