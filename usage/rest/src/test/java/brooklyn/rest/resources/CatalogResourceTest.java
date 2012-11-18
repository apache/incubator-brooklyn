package brooklyn.rest.resources;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.net.URI;
import java.util.Set;

import javax.ws.rs.core.Response;

import org.testng.annotations.Test;

import brooklyn.rest.domain.CatalogEntitySummary;
import brooklyn.rest.testing.BrooklynRestResourceTest;

import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.GenericType;

public class CatalogResourceTest extends BrooklynRestResourceTest {

  @Override
  protected void setUpResources() throws Exception {
    addResource(new CatalogResource());
  }

  @Test
  public void testRegisterCustomEntity() {
    String groovyScript = "package brooklyn.rest.entities.custom\n" +
        "" +
        "import brooklyn.entity.basic.AbstractEntity\n" +
        "import brooklyn.entity.Entity\n" +
        "import brooklyn.event.basic.BasicConfigKey\n" +
        "" +
        "class DummyEntity extends AbstractEntity {\n" +
        "  public static final BasicConfigKey<String> DUMMY_CFG = [ String, \"dummy.config\", \"Dummy Config\" ]\n" +
        "  public DummyEntity(Map properties=[:], Entity owner=null) {\n" +
        "        super(properties, owner)" +
        "  }" +
        "}\n";

    ClientResponse response = client().resource("/v1/catalog")
        .post(ClientResponse.class, groovyScript);

    assertEquals(response.getStatus(), Response.Status.CREATED.getStatusCode());

    Set<String> entities = client().resource("/v1/catalog/entities?name=dummy")
        .get(new GenericType<Set<String>>() {
        });
    assertTrue(entities.contains("brooklyn.rest.entities.custom.DummyEntity"));

    CatalogEntitySummary entity = client().resource(response.getLocation())
        .get(CatalogEntitySummary.class);
    assertTrue(entity.toString().contains("dummy.config"));
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
    assertEquals(entities.size(), 4);
  }

  @Test
  public void testGetCatalogEntityDetails() {
      CatalogEntitySummary details = client().resource(
              URI.create("/v1/catalog/entities/brooklyn.entity.nosql.redis.RedisStore"))
              .get(CatalogEntitySummary.class);
      assertTrue(details.toString().contains("redis.port"));
      assertTrue(details.toString().contains("run.dir"));
  }

  @Test
  public void testListPolicies() {
    Set<String> policies = client().resource("/v1/catalog/policies")
        .get(new GenericType<Set<String>>() {
        });

    assertTrue(policies.size() > 0);
    assertTrue(policies.contains("brooklyn.policy.autoscaling.AutoScalerPolicy"));
  }
}
