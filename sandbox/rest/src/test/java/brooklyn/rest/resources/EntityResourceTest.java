package brooklyn.rest.resources;

import brooklyn.rest.BaseResourceTest;
import com.sun.jersey.api.client.GenericType;
import java.util.Set;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.Test;

public class EntityResourceTest extends BaseResourceTest {

  @Override
  protected void setUpResources() throws Exception {
    addResource(new EntityResource());
  }

  @Test
  public void testListAllEntities() {
    Set<String> entities = client().resource("/v1/entities")
        .get(new GenericType<Set<String>>() {
        });
    assertTrue(entities.size() > 0);
  }

  @Test
  public void testFilterListOfEntitiesByName() {
    Set<String> entities = client().resource("/v1/entities")
        .queryParam("name", "redis").get(new GenericType<Set<String>>() {
        });
    assertEquals(entities.size(), 3);
  }
}
