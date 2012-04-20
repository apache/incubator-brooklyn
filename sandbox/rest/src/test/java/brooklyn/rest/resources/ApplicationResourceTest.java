package brooklyn.rest.resources;

import brooklyn.rest.BaseResourceTest;
import brooklyn.rest.api.ApplicationSpec;
import brooklyn.rest.api.EntitySpec;
import brooklyn.rest.core.ApplicationManager;
import brooklyn.rest.core.LocationStore;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.GenericType;
import java.util.Set;
import javax.ws.rs.core.Response;
import static org.testng.Assert.assertEquals;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

public class ApplicationResourceTest extends BaseResourceTest {

  private ApplicationManager manager;

  private final ApplicationSpec redisSpec = new ApplicationSpec("redis",
      ImmutableSet.of(new EntitySpec("redis", "brooklyn.entity.nosql.redis.RedisStore")),
      ImmutableSet.of("/locations/0"));

  @Override
  protected void setUpResources() throws Exception {
    manager = new ApplicationManager(LocationStore.withLocalhost());
    addResource(new ApplicationSpecResource(manager, new EntityResource()));
  }

  @AfterClass
  @Override
  public void tearDown() throws Exception {
    super.tearDown();
    manager.destroyAll();
  }

  @Test
  public void testDeployRedisApplication() {
    ClientResponse response = client().resource("/applications")
        .post(ClientResponse.class, redisSpec);

    assertEquals(Iterables.size(manager.entries()), 1);
    assertEquals(response.getLocation().getPath(), "/applications/redis");
  }

  @Test(dependsOnMethods = "testDeployRedisApplication")
  public void testGetDetailsAboutAnApplication() {
    // TODO query status of /applications/redis
  }

  @Test(dependsOnMethods = "testDeployRedisApplication")
  public void testListApplications() {
    Set<ApplicationSpec> applications = client().resource("/applications")
        .get(new GenericType<Set<ApplicationSpec>>() {
        });
    assertEquals(applications.size(), 1);
    assertEquals(Iterables.get(applications, 0), redisSpec);
  }

  @Test(dependsOnMethods = "testListApplications")
  public void testDeleteApplication() {
    ClientResponse response = client().resource("/applications/redis")
        .delete(ClientResponse.class);

    assertEquals(response.getStatus(), Response.Status.ACCEPTED.getStatusCode());
    assertEquals(Iterables.size(manager.entries()), 0);
  }
}
