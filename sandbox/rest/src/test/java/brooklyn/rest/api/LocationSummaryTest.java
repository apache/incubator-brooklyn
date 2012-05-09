package brooklyn.rest.api;

import static com.yammer.dropwizard.testing.JsonHelpers.asJson;
import static com.yammer.dropwizard.testing.JsonHelpers.fromJson;
import static com.yammer.dropwizard.testing.JsonHelpers.jsonFixture;
import java.io.IOException;
import static org.testng.Assert.assertEquals;
import org.testng.annotations.Test;

public class LocationSummaryTest {

  final LocationSummary summary = new LocationSummary("123", LocationSpec.localhost());

  @Test
  public void testSerializeToJSON() throws IOException {
    assertEquals(asJson(summary), jsonFixture("fixtures/location-summary.json"));
  }

  @Test
  public void testDeserializeFromJSON() throws IOException {
    assertEquals(fromJson(jsonFixture("fixtures/location-summary.json"), LocationSummary.class), summary);
  }
}
