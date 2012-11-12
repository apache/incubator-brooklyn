package brooklyn.rest.resources;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.net.URI;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;

import javax.ws.rs.core.Response;

import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

import brooklyn.entity.basic.Lifecycle;
import brooklyn.rest.BaseResourceTest;
import brooklyn.rest.BrooklynConfiguration;
import brooklyn.rest.api.Application;
import brooklyn.rest.api.ApplicationSpec;
import brooklyn.rest.api.EntitySpec;
import brooklyn.rest.api.EntitySummary;
import brooklyn.rest.api.SensorSummary;
import brooklyn.rest.core.ApplicationManager;
import brooklyn.rest.core.LocationStore;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.GenericType;

@Test(singleThreaded = true)
public class ApplicationResourceIntegrationTest extends BaseResourceTest {

  private ApplicationManager manager;
  private ExecutorService executorService;

  private final ApplicationSpec redisSpec = ApplicationSpec.builder().name("redis-app").
      entities(ImmutableSet.of(new EntitySpec("redis-ent", "brooklyn.entity.nosql.redis.RedisStore"))).
      locations(ImmutableSet.of("/v1/locations/0")).
      build();

  @Override
  protected void setUpResources() throws Exception {
    executorService = Executors.newCachedThreadPool();
    LocationStore locationStore = LocationStore.withLocalhost();

    manager = new ApplicationManager(new BrooklynConfiguration(), locationStore,
        new CatalogResource(), executorService);

    addResource(new ApplicationResource(manager, locationStore, new CatalogResource()));
    addResource(new EntityResource(manager));
    addResource(new SensorResource(manager));
    addResource(new EffectorResource(manager));
    addResource(new PolicyResource(manager));
  }

  @AfterClass
  @Override
  public void tearDown() throws Exception {
    super.tearDown();
    manager.stop();
    executorService.shutdown();
  }

  @Test(groups="Integration")
  public void testDeployRedisApplication() throws InterruptedException, TimeoutException {
    ClientResponse response = client().resource("/v1/applications")
        .post(ClientResponse.class, redisSpec);

    assertEquals(manager.registryById().size(), 1);
    assertEquals(response.getLocation().getPath(), "/v1/applications/redis-app");

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
    int size = manager.registryById().size();
    ClientResponse response = client().resource("/v1/applications/redis-app")
        .delete(ClientResponse.class);

    waitForPageNotFoundResponse("/v1/applications/redis-app", Application.class);

    assertEquals(response.getStatus(), Response.Status.ACCEPTED.getStatusCode());
    assertEquals(manager.registryById().size(), size-1);
  }

}
