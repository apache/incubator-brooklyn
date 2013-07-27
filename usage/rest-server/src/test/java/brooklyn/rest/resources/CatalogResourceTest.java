package brooklyn.rest.resources;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.awt.Image;
import java.awt.Toolkit;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Set;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.jclouds.compute.domain.ImageBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;
import org.testng.reporters.Files;

import brooklyn.policy.autoscaling.AutoScalerPolicy;
import brooklyn.rest.domain.CatalogEntitySummary;
import brooklyn.rest.domain.CatalogItemSummary;
import brooklyn.rest.testing.BrooklynRestResourceTest;

import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.GenericType;
import com.sun.jersey.api.client.WebResource;

public class CatalogResourceTest extends BrooklynRestResourceTest {

    private static final Logger log = LoggerFactory.getLogger(CatalogResourceTest.class);
    
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
        "  public static final BasicConfigKey<String> DUMMY_CFG = [ String, \"dummy.config\", \"Dummy Config\", \"xxx\" ]\n" +
        "  public DummyEntity(Map properties=[:], Entity parent=null) {\n" +
        "        super(properties, parent)" +
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
    assertTrue(entity.toString().contains("dummy.config"), "ENTITY was: "+entity);
  }

  @Test
  public void testListAllEntities() {
    List<CatalogItemSummary> entities = client().resource("/v1/catalog/entities")
        .get(new GenericType<List<CatalogItemSummary>>() {
        });
    assertTrue(entities.size() > 0);
  }

  @Test
  public void testFilterListOfEntitiesByName() {
    List<CatalogItemSummary> entities = client().resource("/v1/catalog/entities")
            .queryParam("fragment", "reDISclusTER").get(new GenericType<List<CatalogItemSummary>>() {});
    assertEquals(entities.size(), 1);

    log.info("RedisCluster-like entities are: "+entities);
    
    List<CatalogItemSummary> entities2 = client().resource("/v1/catalog/entities")
            .queryParam("regex", "[Rr]ed.[sulC]+ter").get(new GenericType<List<CatalogItemSummary>>() {});
    assertEquals(entities2.size(), 1);
    
    assertEquals(entities, entities2);
    
    List<CatalogItemSummary> entities3 = client().resource("/v1/catalog/entities")
            .queryParam("fragment", "bweqQzZ").get(new GenericType<List<CatalogItemSummary>>() {});
    assertEquals(entities3.size(), 0);
    
    List<CatalogItemSummary> entities4 = client().resource("/v1/catalog/entities")
            .queryParam("regex", "bweq+z+").get(new GenericType<List<CatalogItemSummary>>() {});
    assertEquals(entities4.size(), 0);
  }

  private static final String REDIS_STORE_ICON_URL = "/v1/catalog/icon/brooklyn.entity.nosql.redis.RedisStore";
  
  @Test
  public void testGetCatalogEntityDetails() {
      CatalogEntitySummary details = client().resource(
              URI.create("/v1/catalog/entities/brooklyn.entity.nosql.redis.RedisStore"))
              .get(CatalogEntitySummary.class);
      assertTrue(details.toString().contains("redis.port"), "expected more config, only got: "+details);
      assertTrue(details.getIconUrl().contains(REDIS_STORE_ICON_URL), "expected brooklyn URL for icon image, but got: "+details.getIconUrl());
  }

  @Test
  public void testGetCatalogEntityIconDetails() throws IOException {
      ClientResponse response = client().resource(
              URI.create(REDIS_STORE_ICON_URL)).get(ClientResponse.class);
      response.bufferEntity();
      Assert.assertEquals(response.getType(), MediaType.valueOf("image/png"));
      Image image = Toolkit.getDefaultToolkit().createImage(Files.readFile(response.getEntityInputStream()));
      Assert.assertNotNull(image);
  }

  @Test
  public void testListPolicies() {
    Set<CatalogItemSummary> policies = client().resource("/v1/catalog/policies")
        .get(new GenericType<Set<CatalogItemSummary>>() {
        });

    assertTrue(policies.size() > 0);
    CatalogItemSummary asp = null;
    for (CatalogItemSummary p: policies) {
        if (AutoScalerPolicy.class.getName().equals(p.getType()))
            asp = p;
    }
    Assert.assertNotNull(asp, "didn't find AutoScalerPolicy");
  }
  
}
