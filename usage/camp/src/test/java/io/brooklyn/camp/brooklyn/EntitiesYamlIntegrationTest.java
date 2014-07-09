package io.brooklyn.camp.brooklyn;

import static org.testng.Assert.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import com.google.common.collect.FluentIterable;
import com.google.common.collect.Iterables;

import brooklyn.entity.Entity;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.proxy.nginx.NginxController;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.webapp.ControlledDynamicWebAppCluster;
import brooklyn.entity.webapp.tomcat.TomcatServer;

public class EntitiesYamlIntegrationTest extends AbstractYamlTest {

    private static final Logger LOG = LoggerFactory.getLogger(EntitiesYamlIntegrationTest.class);

    @Test(groups = "Integration")
    public void testStartTomcatCluster() throws Exception {
        Entity app = createAndStartApplication("test-tomcat-cluster.yaml");
        waitForApplicationTasks(app);

        assertNotNull(app);
        assertEquals(app.getChildren().size(), 1);
        final Entity entity = Iterables.getOnlyElement(app.getChildren());
        assertTrue(entity instanceof ControlledDynamicWebAppCluster, "entity="+entity);
        ControlledDynamicWebAppCluster cluster = (ControlledDynamicWebAppCluster) entity;

        assertTrue(cluster.getController() instanceof NginxController, "controller="+cluster.getController());
        Iterable<TomcatServer> tomcats = FluentIterable.from(cluster.getCluster().getMembers()).filter(TomcatServer.class);
        assertEquals(Iterables.size(tomcats), 2);
        for (TomcatServer tomcat : tomcats) {
            assertTrue(tomcat.getAttribute(TomcatServer.SERVICE_UP), "serviceup");
        }

        EntitySpec<?> spec = entity.getConfig(DynamicCluster.MEMBER_SPEC);
        assertNotNull(spec);
        assertEquals(spec.getType(), TomcatServer.class);
        assertEquals(spec.getConfig().get(DynamicCluster.QUARANTINE_FAILED_ENTITIES), Boolean.FALSE);
        assertEquals(spec.getConfig().get(DynamicCluster.INITIAL_QUORUM_SIZE), 2);
    }


    @Override
    protected Logger getLogger() {
        return LOG;
    }
}
