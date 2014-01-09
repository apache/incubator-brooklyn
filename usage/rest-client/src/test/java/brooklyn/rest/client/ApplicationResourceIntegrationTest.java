package brooklyn.rest.client;

import static brooklyn.rest.BrooklynRestApiLauncher.startServer;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.util.Collection;

import javax.ws.rs.core.Response;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.webapp.WebAppContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import brooklyn.config.BrooklynServiceAttributes;
import brooklyn.entity.Application;
import brooklyn.entity.basic.AbstractApplication;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.location.basic.BasicLocationRegistry;
import brooklyn.management.ManagementContext;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.rest.BrooklynRestApiLauncherTest;
import brooklyn.rest.domain.ApplicationSummary;
import brooklyn.rest.domain.EntitySummary;
import brooklyn.rest.domain.SensorSummary;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;

@Test(singleThreaded = true)
public class ApplicationResourceIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(ApplicationResourceIntegrationTest.class);

    private final String redisSpec = "{\"name\": \"redis-app\", \"type\": \"brooklyn.entity.nosql.redis.RedisStore\", \"locations\": [ \"localhost\"]}";

    private ManagementContext manager;

    protected synchronized ManagementContext getManagementContext() throws Exception {
        if (manager == null) {
            manager = new LocalManagementContext();
            BrooklynRestApiLauncherTest.forceUseOfDefaultCatalogWithJavaClassPath(manager);
            BasicLocationRegistry.setupLocationRegistryForTesting(manager);
            BrooklynRestApiLauncherTest.enableAnyoneLogin(manager);
        }
        return manager;
    }

    BrooklynApi api;

    @BeforeClass(groups = "Integration")
    public void setUp() throws Exception {
        WebAppContext context;

        // running in source mode; need to use special classpath        
        context = new WebAppContext("src/test/webapp", "/");
        context.setExtraClasspath("./target/test-rest-server/");
        context.setAttribute(BrooklynServiceAttributes.BROOKLYN_MANAGEMENT_CONTEXT, getManagementContext());

        Server server = startServer(manager, context, "from WAR at " + context.getWar());

        api = new BrooklynApi("http://localhost:" + server.getConnectors()[0].getPort() + "/");
    }

    @AfterClass(alwaysRun = true)
    public void tearDown() throws Exception {
        for (Application app : getManagementContext().getApplications()) {
            try {
                ((AbstractApplication) app).stop();
            } catch (Exception e) {
                log.warn("Error stopping app " + app + " during test teardown: " + e);
            }
        }
    }

    @Test(groups = "Integration")
    public void testDeployRedisApplication() throws Exception {
        Response response = api.getApplicationApi().createPoly(redisSpec.getBytes());
        assertEquals(response.getStatus(), 201);
        assertEquals(getManagementContext().getApplications().size(), 1);
        String entityId = getManagementContext().getApplications().iterator().next().getChildren().iterator().next().getId();
        while (!api.getSensorApi().get("redis-app", entityId, "service.state").equals(Lifecycle.RUNNING.toString())) {
            Thread.sleep(100);
        }
    }

    @Test(groups = "Integration", dependsOnMethods = "testDeployRedisApplication")
    public void testListEntities() {
        Collection<EntitySummary> entities = api.getEntityApi().list("redis-app");
        Assert.assertFalse(entities.isEmpty());
    }

    @Test(groups = "Integration", dependsOnMethods = "testDeployRedisApplication")
    public void testListSensorsRedis() throws Exception {
        String entityId = getManagementContext().getApplications().iterator().next().getChildren().iterator().next().getId();
        Collection<SensorSummary> sensors = api.getSensorApi().list("redis-app", entityId);
        assertTrue(sensors.size() > 0);
        SensorSummary uptime = Iterables.find(sensors, new Predicate<SensorSummary>() {
            @Override
            public boolean apply(SensorSummary sensorSummary) {
                return sensorSummary.getName().equals("redis.uptime");
            }
        });
        assertEquals(uptime.getType(), "java.lang.Integer");
    }

    @Test(groups = "Integration", dependsOnMethods = {"testListSensorsRedis", "testListEntities"})
    public void testTriggerRedisStopEffector() throws Exception {
        String entityId = getManagementContext().getApplications().iterator().next().getChildren().iterator().next().getId();
        Response response = api.getEffectorApi().invoke("redis-app", entityId, "stop", "5000", ImmutableMap.<String, String>of());

        assertEquals(response.getStatus(), Response.Status.ACCEPTED.getStatusCode());

        while (!api.getSensorApi().get("redis-app", entityId, "service.state").equals(Lifecycle.STOPPED.toString())) {
            Thread.sleep(5000);
        }
    }

    @Test(groups = "Integration", dependsOnMethods = "testTriggerRedisStopEffector")
    public void testDeleteRedisApplication() throws Exception {
        int size = getManagementContext().getApplications().size();
        Response response = api.getApplicationApi().delete("redis-app");
        Assert.assertNotNull(response);
        ApplicationSummary summary = null;
        try {
            for (int i = 0; i < 100 && summary == null; i++) {
                summary = api.getApplicationApi().get("redis-app");
                Thread.sleep(500);
            }
            fail("Redis app failed to disappear!");
        } catch (Exception failure) {
            // expected -- it will be a ClientResponseFailure but that class is deprecated so catching all
            // and asserting contains the word 404
            Assert.assertTrue(failure.toString().indexOf("404") >= 0);
        }

        assertNull(summary);
        assertEquals(getManagementContext().getApplications().size(), size - 1);
    }

}
