package brooklyn.rest.resources;

import brooklyn.entity.basic.Lifecycle;
import brooklyn.rest.BaseResourceTest;
import brooklyn.rest.BrooklynConfiguration;
import brooklyn.rest.api.Application;
import brooklyn.rest.api.ApplicationSpec;
import brooklyn.rest.api.EntitySpec;
import brooklyn.rest.core.ApplicationManager;
import brooklyn.rest.core.LocationStore;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.GenericType;
import com.sun.jersey.api.client.UniformInterfaceException;
import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import javax.ws.rs.core.Response;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

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

    manager = new ApplicationManager(new BrooklynConfiguration(), locationStore, executorService);

    addResource(new ApplicationResource(manager, locationStore, new CatalogResource()));
    addResource(new EntityResource(manager));
    addResource(new SensorResource(manager));
    addResource(new EffectorResource(manager, executorService));
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

    waitForApplicationToBeRunning(response);
  }

  private void waitForApplicationToBeRunning(ClientResponse response) throws InterruptedException, TimeoutException {
    int count = 0;
    while (getApplicationStatus(response.getLocation()) != Application.Status.RUNNING) {
      Thread.sleep(7000);
      count += 1;
      if (count == 20) {
        throw new TimeoutException("Taking to long to get to RUNNING.");
      }
    }
  }

  private Application.Status getApplicationStatus(URI uri) {
    return client().resource(uri).get(Application.class).getStatus();
  }

  @Test(dependsOnMethods = "testDeployRedisApplication")
  public void testListEntities() {
    Set<URI> entities = client().resource("/v1/applications/redis-app/entities")
        .get(new GenericType<Set<URI>>() {
        });
    for (URI ref : entities) {
      client().resource(ref).get(ClientResponse.class);
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
    Set<URI> sensors = client().resource("/v1/applications/redis-app/entities/redis-ent/sensors")
        .get(new GenericType<Set<URI>>() {
        });
    assertTrue(sensors.contains(
        URI.create("/v1/applications/redis-app/entities/redis-ent/sensors/redis.uptime")));
  }

  @Test(dependsOnMethods = "testListSensors")
  public void testReadAllSensors() {
    Set<URI> sensors = client().resource("/v1/applications/redis-app/entities/redis-ent/sensors")
        .get(new GenericType<Set<URI>>() {
        });

    Map<String, String> readings = Maps.newHashMap();
    for (URI ref : sensors) {
      readings.put(ref.toString(), client().resource(ref).get(String.class));
    }

    assertEquals(readings.get("/v1/applications/redis-app/entities/redis-ent/sensors/service.state"), "running");
    assertEquals(readings.get("/v1/applications/redis-app/entities/redis-ent/sensors/redis.port"), "6379");
  }

  @Test(dependsOnMethods = "testDeployRedisApplication")
  public void testListEffectors() {
    Set<URI> effectors = client().resource("/v1/applications/redis-app/entities/redis-ent/effectors")
        .get(new GenericType<Set<URI>>() {
        });

    assertTrue(effectors.contains(
        URI.create("/v1/applications/redis-app/entities/redis-ent/effectors/stop")));
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

    waitForPageNotFoundResponse("/v1/applications/redis-app");

    assertEquals(response.getStatus(), Response.Status.ACCEPTED.getStatusCode());
    assertEquals(manager.registry().size(), 0);
  }

  private void waitForPageNotFoundResponse(String resource) throws InterruptedException, TimeoutException {
    int count = 0;
    while (true) {
      try {
        client().resource(resource).get(Application.class);

      } catch (UniformInterfaceException e) {
        if (e.getResponse().getStatus() == 404) {
          break;
        }
      }
      Thread.sleep(5000);
      count += 1;
      if (count > 20) {
        throw new TimeoutException("Timeout while waiting for 404 on " + resource);
      }
    }
  }
}
