package brooklyn.rest.domain;

import brooklyn.rest.domain.ApplicationSpec;
import brooklyn.rest.domain.EntitySpec;

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

  final ApplicationSpec applicationSpec = ApplicationSpec.builder().name("myapp").
          entities(ImmutableSet.of(entitySpec)).locations(ImmutableSet.of("/v1/locations/1")).
          build();

  @Test
  public void testSerializeToJSON() throws IOException {
    assertEquals(asJson(applicationSpec), jsonFixture("fixtures/application-spec.json"));
  }

  @Test
  public void testDeserializeFromJSON() throws IOException {
    assertEquals(fromJson(jsonFixture("fixtures/application-spec.json"),
        ApplicationSpec.class), applicationSpec);
  }

}
