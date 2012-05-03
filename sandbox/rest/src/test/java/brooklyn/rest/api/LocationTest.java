package brooklyn.rest.api;

import static com.yammer.dropwizard.testing.JsonHelpers.asJson;
import static com.yammer.dropwizard.testing.JsonHelpers.fromJson;
import static com.yammer.dropwizard.testing.JsonHelpers.jsonFixture;
import java.io.IOException;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import org.testng.annotations.Test;

public class LocationTest {

  final Location location = Location.localhost();

  @Test
  public void testSerializeToJSON() throws IOException {
    assertEquals(asJson(location), jsonFixture("fixtures/location.json"));
  }

  @Test
  public void testDeserializeFromJSON() throws IOException {
    assertEquals(fromJson(jsonFixture("fixtures/location.json"), Location.class), location);
  }

  @Test
  public void testDeserializeFromJSONWithNoCredential() throws IOException {
    Location loaded = fromJson(jsonFixture("fixtures/location-without-credential.json"), Location.class);

    assertNull(loaded.getCredential());
    assertEquals(loaded.getProvider(), location.getProvider());
    assertEquals(loaded.getIdentity(), location.getIdentity());
  }
}
