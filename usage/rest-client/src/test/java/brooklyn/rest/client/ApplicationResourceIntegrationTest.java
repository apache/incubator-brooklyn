package brooklyn.rest.client;

import brooklyn.config.BrooklynServiceAttributes;
import brooklyn.entity.Application;
import brooklyn.entity.basic.AbstractApplication;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.location.basic.BasicLocationRegistry;
import brooklyn.management.ManagementContext;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.rest.BrooklynRestApiLauncherTest;
import brooklyn.rest.domain.*;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.webapp.WebAppContext;
import org.jboss.resteasy.client.ClientResponseFailure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import javax.ws.rs.core.Response;
import java.net.URI;
import java.util.Collection;

import static brooklyn.rest.BrooklynRestApiLauncher.startServer;
import static org.testng.Assert.*;

@Test(singleThreaded = true)
public class ApplicationResourceIntegrationTest {

    @SuppressWarnings("unused")
    private static final Logger log = LoggerFactory.getLogger(ApplicationResourceIntegrationTest.class);

    private final ApplicationSpec redisSpec = ApplicationSpec.builder().name("redis-app").
            entities(ImmutableSet.of(new EntitySpec("redis-ent", "brooklyn.entity.nosql.redis.RedisStore"))).
            locations(ImmutableSet.of("localhost")).
            build();

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

        Server server = startServer(context, "from WAR at " + context.getWar());

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

        Response response = api.getApplicationApi().create(redisSpec);

        assertEquals(response.getStatus(), 201);
        assertEquals(getManagementContext().getApplications().size(), 1);

        while (!api.getSensorApi().get("redis-app", "redis-ent", "service.state").equals(Lifecycle.RUNNING.toString())) {
            Thread.sleep(100);
        }
    }

    @Test(groups = "Integration", dependsOnMethods = "testDeployRedisApplication")
    public void testListEntities() {
        Collection<EntitySummary> entities = api.getEntityApi().list("redis-app");
    }

    @Test(groups = "Integration", dependsOnMethods = "testDeployRedisApplication")
    public void testListSensorsRedis() {
        Collection<SensorSummary> sensors = api.getSensorApi().list("redis-app", "redis-ent");
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
        Response response = api.getEffectorApi().invoke("redis-app", "redis-ent", "stop", "5000", ImmutableMap.<String, String>of());

        assertEquals(response.getStatus(), Response.Status.ACCEPTED.getStatusCode());

        while (!api.getSensorApi().get("redis-app", "redis-ent", "service.state").equals(Lifecycle.STOPPED.toString())) {
            Thread.sleep(5000);
        }
    }

    @Test(groups = "Integration", dependsOnMethods = "testTriggerRedisStopEffector")
    public void testDeleteRedisApplication() throws Exception {
        int size = getManagementContext().getApplications().size();
        Response response = api.getApplicationApi().delete("redis-app");
        ApplicationSummary summary = null;
        try {
            for (int i = 0; i < 100 && summary == null; i++) {
                summary = api.getApplicationApi().get("redis-app");
                Thread.sleep(500);
            }
            fail("Redis app failed to disappear!");
        } catch (ClientResponseFailure failure) {
        }

        assertNull(summary);
        assertEquals(getManagementContext().getApplications().size(), size - 1);
    }

}
