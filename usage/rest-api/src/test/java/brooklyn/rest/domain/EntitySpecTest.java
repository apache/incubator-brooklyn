package brooklyn.rest.domain;

import static com.yammer.dropwizard.testing.JsonHelpers.asJson;
import static com.yammer.dropwizard.testing.JsonHelpers.fromJson;
import static com.yammer.dropwizard.testing.JsonHelpers.jsonFixture;
import static org.testng.Assert.assertEquals;
import org.testng.annotations.Test;

import brooklyn.rest.domain.EntitySpec;

import java.io.IOException;

public class EntitySpecTest {

  final EntitySpec entitySpec = new EntitySpec("Vanilla Java App", "brooklyn.entity.java.VanillaJavaApp");

  @Test
  public void testSerializeToJSON() throws IOException {
    assertEquals(asJson(new EntitySpec[]{entitySpec}), jsonFixture("fixtures/entity.json"));
  }

  @Test
  public void testDeserializeFromJSON() throws IOException {
    assertEquals(fromJson(jsonFixture("fixtures/entity.json"), EntitySpec[].class), new EntitySpec[]{entitySpec});
  }

  @Test
  public void testDeserializeFromJSONOnlyWithType() throws IOException {
    EntitySpec actual = fromJson(jsonFixture("fixtures/entity-only-type.json"), EntitySpec.class);
    assertEquals(actual.getName(), actual.getType());
    assertEquals(actual.getConfig().size(), 0);
  }
}
