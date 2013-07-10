package brooklyn.rest.resources;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.net.URI;
import java.util.Set;
import java.util.concurrent.TimeoutException;

import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

import brooklyn.entity.basic.Lifecycle;
import brooklyn.rest.domain.ApplicationSpec;
import brooklyn.rest.domain.ApplicationSummary;
import brooklyn.rest.domain.EntitySpec;
import brooklyn.rest.domain.EntitySummary;
import brooklyn.rest.domain.SensorSummary;
import brooklyn.rest.testing.BrooklynRestResourceTest;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.GenericType;

@Test(singleThreaded = true)
public class ApplicationResourceIntegrationTest extends BrooklynRestResourceTest {

    @SuppressWarnings("unused")
    private static final Logger log = LoggerFactory.getLogger(ApplicationResourceIntegrationTest.class);

    private final ApplicationSpec redisSpec = ApplicationSpec.builder().name("redis-app").
            entities(ImmutableSet.of(new EntitySpec("redis-ent", "brooklyn.entity.nosql.redis.RedisStore"))).
            locations(ImmutableSet.of("localhost")).
            build();

    @Override
    protected void setUpResources() throws Exception {
        addResources();
    }

    @AfterClass(alwaysRun=true)
    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        stopManager();
    }

    @Test(groups="Integration")
    public void testDeployRedisApplication() throws InterruptedException, TimeoutException {
        ClientResponse response = client().resource("/v1/applications")
                .post(ClientResponse.class, redisSpec);

        assertEquals(response.getStatus(), 201);
        assertEquals(getManagementContext().getApplications().size(), 1);
        assertTrue(response.getLocation().getPath().startsWith("/v1/applications/"), "path="+response.getLocation().getPath()); // path uses id, rather than app name

        waitForApplicationToBeRunning(response.getLocation());
    }

    @Test(groups="Integration", dependsOnMethods = "testDeployRedisApplication")
    public void testListEntities() {
        Set<EntitySummary> entities = client().resource("/v1/applications/redis-app/entities")
                .get(new GenericType<Set<EntitySummary>>() {
                });

        for (EntitySummary entity : entities) {
            client().resource(entity.getLinks().get("self")).get(ClientResponse.class);
            // TODO assertions on the above call?

            Set<EntitySummary> children = client().resource(entity.getLinks().get("children"))
                    .get(new GenericType<Set<EntitySummary>>() {
                    });
            assertEquals(children.size(), 0);
        }
    }

    @Test(groups="Integration", dependsOnMethods = "testDeployRedisApplication")
    public void testListSensorsRedis() {
        Set<SensorSummary> sensors = client().resource("/v1/applications/redis-app/entities/redis-ent/sensors")
                .get(new GenericType<Set<SensorSummary>>() {
                });
        assertTrue(sensors.size() > 0);
        SensorSummary uptime = Iterables.find(sensors, new Predicate<SensorSummary>() {
            @Override
            public boolean apply(SensorSummary sensorSummary) {
                return sensorSummary.getName().equals("redis.uptime");
            }
        });
        assertEquals(uptime.getType(), "java.lang.Integer");
    }

    @Test(groups="Integration", dependsOnMethods = { "testListSensorsRedis", "testListEntities" })
    public void testTriggerRedisStopEffector() throws InterruptedException {
        ClientResponse response = client().resource("/v1/applications/redis-app/entities/redis-ent/effectors/stop")
                .post(ClientResponse.class, ImmutableMap.of());

        assertEquals(response.getStatus(), Response.Status.ACCEPTED.getStatusCode());

        URI stateSensor = URI.create("/v1/applications/redis-app/entities/redis-ent/sensors/service.state");
        while (!client().resource(stateSensor).get(String.class).equals(Lifecycle.STOPPED.toString())) {
            Thread.sleep(5000);
        }
    }
    @Test(groups="Integration", dependsOnMethods = "testTriggerRedisStopEffector" )
    public void testDeleteRedisApplication() throws TimeoutException, InterruptedException {
        int size = getManagementContext().getApplications().size();
        ClientResponse response = client().resource("/v1/applications/redis-app")
                .delete(ClientResponse.class);

        waitForPageNotFoundResponse("/v1/applications/redis-app", ApplicationSummary.class);

        assertEquals(response.getStatus(), Response.Status.ACCEPTED.getStatusCode());
        assertEquals(getManagementContext().getApplications().size(), size-1);
    }

}
