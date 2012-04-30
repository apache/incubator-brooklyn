package brooklyn.rest.resources;

import brooklyn.rest.BaseResourceTest;
import com.google.common.collect.ImmutableSet;
import com.sun.jersey.api.client.GenericType;
import java.net.URI;
import java.util.Set;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;
import org.testng.annotations.Test;

public class CatalogResourceTest extends BaseResourceTest {

  @Override
  protected void setUpResources() throws Exception {
    addResource(new CatalogResource());
  }

  @Test
  public void testListAllEntities() {
    Set<String> entities = client().resource("/v1/catalog/entities")
        .get(new GenericType<Set<String>>() {
        });
    assertTrue(entities.size() > 0);
  }

  @Test
  public void testFilterListOfEntitiesByName() {
    Set<String> entities = client().resource("/v1/catalog/entities")
        .queryParam("name", "redis").get(new GenericType<Set<String>>() {
        });
    assertEquals(entities.size(), 3);
  }

  @Test
  public void testGetConfigKeys() {
    Set<String> keys = client().resource(
        URI.create("/v1/catalog/entities/brooklyn.entity.nosql.redis.RedisStore"))
        .get(new GenericType<Set<String>>() {
        });
    assertTrue(keys.containsAll(ImmutableSet.of("redis.port", "install.version", "run.dir")));
  }

  @Test
  public void testListPolicies() {
    Set<String> policies = client().resource("/v1/catalog/policies")
        .get(new GenericType<Set<String>>() {
        });

    assertTrue(policies.size() > 0);
    assertTrue(policies.contains("brooklyn.policy.resizing.ResizingPolicy"));
  }
}
