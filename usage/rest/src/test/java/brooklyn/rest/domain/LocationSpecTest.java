package brooklyn.rest.domain;

import static com.yammer.dropwizard.testing.JsonHelpers.asJson;
import static com.yammer.dropwizard.testing.JsonHelpers.fromJson;
import static com.yammer.dropwizard.testing.JsonHelpers.jsonFixture;
import java.io.IOException;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import org.testng.annotations.Test;

import brooklyn.rest.domain.LocationSpec;

public class LocationSpecTest {

  final LocationSpec locationSpec = LocationSpec.localhost();

  @Test
  public void testSerializeToJSON() throws IOException {
    assertEquals(asJson(locationSpec), jsonFixture("fixtures/location.json"));
  }

  @Test
  public void testDeserializeFromJSON() throws IOException {
    assertEquals(fromJson(jsonFixture("fixtures/location.json"), LocationSpec.class), locationSpec);
  }

  @Test
  public void testDeserializeFromJSONWithNoCredential() throws IOException {
    LocationSpec loaded = fromJson(jsonFixture("fixtures/location-without-credential.json"), LocationSpec.class);

    assertEquals(loaded.getSpec(), locationSpec.getSpec());
    
    assertEquals(loaded.getConfig().size(), 1);
    assertEquals(loaded.getConfig().get("identity"), "bob");
    assertNull(loaded.getConfig().get("credential"));
  }
}
