package brooklyn.rest.api;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import static com.yammer.dropwizard.testing.JsonHelpers.asJson;
import static com.yammer.dropwizard.testing.JsonHelpers.fromJson;
import static com.yammer.dropwizard.testing.JsonHelpers.jsonFixture;
import java.io.IOException;
import static org.testng.Assert.assertEquals;
import org.testng.annotations.Test;

public class ApplicationSpecTest {
  final EntitySpec entitySpec = new EntitySpec("Vanilla Java App", "brooklyn.entity.java.VanillaJavaApp",
      ImmutableMap.<String, String>of(
          "initialSize", "1",
          "creationScriptUrl", "http://my.brooklyn.io/storage/foo.sql"
      ));
  final ApplicationSpec applicationSpec = new ApplicationSpec("myapp", ImmutableSet.of(entitySpec),
      ImmutableSet.of("/locations/1"));

  @Test
  public void testSerializeToJSON() throws IOException {
    assertEquals(asJson(applicationSpec), jsonFixture("fixtures/application.json"));
  }

  @Test
  public void testDeserializeFromJSON() throws IOException {
    assertEquals(fromJson(jsonFixture("fixtures/application.json"),
        ApplicationSpec.class), applicationSpec);
  }

}
