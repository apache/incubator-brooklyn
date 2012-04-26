package brooklyn.rest;

import brooklyn.rest.api.Application;
import brooklyn.rest.api.ApplicationSpec;
import brooklyn.rest.api.EntitySpec;
import brooklyn.rest.core.ApplicationManager;
import brooklyn.rest.core.LocationStore;
import brooklyn.rest.resources.ApplicationResource;
import brooklyn.rest.resources.EffectorResource;
import brooklyn.rest.resources.EntityResource;
import brooklyn.rest.resources.SensorResource;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.sun.jersey.api.client.ClientResponse;
import java.net.URI;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import static org.testng.Assert.assertEquals;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

@Test(enabled = false)
public class WebClusterTest extends BaseResourceTest {

  private ExecutorService executorService;
  private ApplicationManager manager;

  @Override
  protected void setUpResources() throws Exception {
    executorService = Executors.newCachedThreadPool();
    LocationStore locationStore = LocationStore.withLocalhost();

    manager = new ApplicationManager(new BrooklynConfiguration(), locationStore, executorService);

    addResource(new ApplicationResource(manager, locationStore, new EntityResource()));
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
  public void testStartWebCluster() throws TimeoutException, InterruptedException {
    ApplicationSpec clusterSpec = new ApplicationSpec("web-cluster",
        ImmutableSet.of(
            new EntitySpec("webserver", "brooklyn.entity.webapp.jboss.JBoss7Server",
                ImmutableMap.of("", "")),
            new EntitySpec("db", "brooklyn.entity.nosql.redis.RedisStore")
        ),
        ImmutableSet.of("/v1/locations/0"));

    ClientResponse response = client().resource("/v1/applications")
        .post(ClientResponse.class, clusterSpec);

    assertEquals(manager.registry().size(), 1);
    assertEquals(response.getLocation().getPath(), "/v1/applications/web-cluster");

    waitForApplicationToBeRunning(response);

    // TODO perform HTTP request
  }

  private void waitForApplicationToBeRunning(ClientResponse response) throws InterruptedException, TimeoutException {
    while (getApplicationStatus(response.getLocation()) != Application.Status.RUNNING) {
      Thread.sleep(10000);
    }
  }

  private Application.Status getApplicationStatus(URI uri) {
    return client().resource(uri).get(Application.class).getStatus();
  }

}
