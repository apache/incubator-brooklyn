package brooklyn.rest.resources;

import brooklyn.entity.basic.Lifecycle;
import brooklyn.rest.BaseResourceTest;
import brooklyn.rest.BrooklynConfiguration;
import brooklyn.rest.api.ApiError;
import brooklyn.rest.api.Application;
import brooklyn.rest.api.ApplicationSpec;
import brooklyn.rest.api.EffectorSummary;
import brooklyn.rest.api.EntitySpec;
import brooklyn.rest.api.EntitySummary;
import brooklyn.rest.api.SensorSummary;
import brooklyn.rest.core.ApplicationManager;
import brooklyn.rest.core.LocationStore;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.GenericType;
import com.sun.jersey.api.client.UniformInterfaceException;
import com.yammer.dropwizard.jersey.DropwizardResourceConfig;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

import javax.ws.rs.core.Response;
import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;

import static com.google.common.collect.Iterables.find;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

@Test(singleThreaded = true)
public class ApplicationResourceTest extends BaseResourceTest {

  private ApplicationManager manager;
  private ExecutorService executorService;

  private final ApplicationSpec redisSpec = new ApplicationSpec("redis-app",
      ImmutableSet.of(new EntitySpec("redis-ent", "brooklyn.entity.nosql.redis.RedisStore")),
      ImmutableSet.of("/v1/locations/0"));

  @Override
  protected void setUpResources() throws Exception {
    executorService = Executors.newCachedThreadPool();
    LocationStore locationStore = LocationStore.withLocalhost();

    manager = new ApplicationManager(new BrooklynConfiguration(), locationStore,
        new CatalogResource(), executorService);

    addResource(new ApplicationResource(manager, locationStore, new CatalogResource()));
    addResource(new EntityResource(manager));
    addResource(new SensorResource(manager));
    addResource(new EffectorResource(manager, executorService));
    addResource(new DropwizardResourceConfig());
  }

  @AfterClass
  @Override
  public void tearDown() throws Exception {
    super.tearDown();
    manager.stop();
    executorService.shutdown();
  }

  @Test
  public void testGetUndefinedApplication() {
    try {
      client().resource("/v1/applications/dummy-not-found").get(Application.class);
    } catch (UniformInterfaceException e) {
      assertEquals(e.getResponse().getStatus(), 404);
    }
  }

  @Test
  public void testDeployRedisApplication() throws InterruptedException, TimeoutException {
    ClientResponse response = client().resource("/v1/applications")
        .post(ClientResponse.class, redisSpec);

    assertEquals(manager.registry().size(), 1);
    assertEquals(response.getLocation().getPath(), "/v1/applications/redis-app");

    waitForApplicationToBeRunning(response.getLocation());
  }

  @Test
  public void testDeployWithInvalidEntityType() {
    try {
      client().resource("/v1/applications").post(
          new ApplicationSpec("invalid-app",
              ImmutableSet.of(new EntitySpec("invalid-ent", "not.existing.entity")),
              ImmutableSet.<String>of("/v1/locations/0"))
      );

    } catch (UniformInterfaceException e) {
      ApiError error = e.getResponse().getEntity(ApiError.class);
      assertEquals(error.getMessage(), "Undefined entity type 'not.existing.entity'");
    }
  }

  @Test
  public void testDeployWithInvalidLocation() {
    try {
      client().resource("/v1/applications").post(
          new ApplicationSpec("invalid-app",
              ImmutableSet.<EntitySpec>of(new EntitySpec("redis-ent", "brooklyn.entity.nosql.redis.RedisStore")),
              ImmutableSet.of("/v1/locations/3423"))
      );

    } catch (UniformInterfaceException e) {
      ApiError error = e.getResponse().getEntity(ApiError.class);
      assertEquals(error.getMessage(), "Undefined location '/v1/locations/3423'");
    }
  }

  @Test(dependsOnMethods = "testDeployRedisApplication")
  public void testListEntities() {
    Set<EntitySummary> entities = client().resource("/v1/applications/redis-app/entities")
        .get(new GenericType<Set<EntitySummary>>() {
        });

    for (EntitySummary entity : entities) {
      client().resource(entity.getLinks().get("self")).get(ClientResponse.class);

      Set<EntitySummary> children = client().resource(entity.getLinks().get("children"))
          .get(new GenericType<Set<EntitySummary>>() {
          });
      assertEquals(children.size(), 0);
    }
  }

  @Test(dependsOnMethods = "testDeployRedisApplication")
  public void testListApplications() {
    Set<Application> applications = client().resource("/v1/applications")
        .get(new GenericType<Set<Application>>() {
        });
    assertEquals(applications.size(), 1);
    assertEquals(Iterables.get(applications, 0).getSpec(), redisSpec);
  }

  @Test(dependsOnMethods = "testDeployRedisApplication")
  public void testListSensors() {
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

  @Test(dependsOnMethods = "testListSensors")
  public void testReadAllSensors() {
    Set<SensorSummary> sensors = client().resource("/v1/applications/redis-app/entities/redis-ent/sensors")
        .get(new GenericType<Set<SensorSummary>>() {
        });

    Map<String, String> readings = Maps.newHashMap();
    for (SensorSummary sensor : sensors) {
      readings.put(sensor.getName(), client().resource(sensor.getLinks().get("self")).get(String.class));
    }

    assertEquals(readings.get("service.state"), "running");
    assertEquals(readings.get("redis.port"), "6379");
  }

  @Test(dependsOnMethods = "testDeployRedisApplication")
  public void testListEffectors() {
    Set<EffectorSummary> effectors = client().resource("/v1/applications/redis-app/entities/redis-ent/effectors")
        .get(new GenericType<Set<EffectorSummary>>() {
        });

    assertTrue(effectors.size() > 0);

    EffectorSummary stopEffector = find(effectors, new Predicate<EffectorSummary>() {
      @Override
      public boolean apply(EffectorSummary input) {
        return input.getName().equals("stop");
      }
    });
    assertEquals(stopEffector.getReturnType(), "void");
  }

  @Test(dependsOnMethods = "testReadAllSensors")
  public void testTriggerStopEffector() throws InterruptedException {
    ClientResponse response = client().resource("/v1/applications/redis-app/entities/redis-ent/effectors/stop")
        .post(ClientResponse.class, ImmutableMap.of());

    assertEquals(response.getStatus(), Response.Status.ACCEPTED.getStatusCode());

    URI stateSensor = URI.create("/v1/applications/redis-app/entities/redis-ent/sensors/service.state");
    while (!client().resource(stateSensor).get(String.class).equals(Lifecycle.STOPPED.toString())) {
      Thread.sleep(5000);
    }
  }

  @Test(dependsOnMethods = {"testListEffectors", "testTriggerStopEffector", "testListApplications"})
  public void testDeleteApplication() throws TimeoutException, InterruptedException {
    ClientResponse response = client().resource("/v1/applications/redis-app")
        .delete(ClientResponse.class);

    waitForPageNotFoundResponse("/v1/applications/redis-app", Application.class);

    assertEquals(response.getStatus(), Response.Status.ACCEPTED.getStatusCode());
    assertEquals(manager.registry().size(), 0);
  }
}
