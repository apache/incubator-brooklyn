package brooklyn.rest.api;

import com.google.common.collect.ImmutableMap;
import static com.yammer.dropwizard.testing.JsonHelpers.asJson;
import static com.yammer.dropwizard.testing.JsonHelpers.fromJson;
import static com.yammer.dropwizard.testing.JsonHelpers.jsonFixture;
import java.io.IOException;
import static org.testng.Assert.assertEquals;
import org.testng.annotations.Test;

public class EntityTest {

  final Entity entity = new Entity("brooklyn.entity.java.VanillaJavaApp", ImmutableMap.<String, String>of());

  @Test
  public void testSerializeToJSON() throws IOException {
    assertEquals(asJson(entity), jsonFixture("fixtures/entity.json"));
  }

  @Test
  public void testDeserializeFromJSON() throws IOException {
    assertEquals(fromJson(jsonFixture("fixtures/entity.json"), Entity.class), entity);
  }
}
