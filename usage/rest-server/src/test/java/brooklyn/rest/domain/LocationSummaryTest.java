package brooklyn.rest.domain;

import static com.yammer.dropwizard.testing.JsonHelpers.asJson;
import static com.yammer.dropwizard.testing.JsonHelpers.fromJson;
import static com.yammer.dropwizard.testing.JsonHelpers.jsonFixture;
import static org.testng.Assert.assertEquals;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import brooklyn.rest.transform.LocationTransformer;
import org.codehaus.jackson.type.TypeReference;
import org.testng.annotations.Test;

public class LocationSummaryTest {

  final LocationSummary summary = LocationTransformer.newInstance("123", LocationSpec.localhost());

  @Test
  public void testSerializeToJSON() throws IOException {
    assertEquals(asJson(summary), jsonFixture("fixtures/location-summary.json"));
  }

  @Test
  public void testDeserializeFromJSON() throws IOException {
    assertEquals(fromJson(jsonFixture("fixtures/location-summary.json"), LocationSummary.class), summary);
  }
  
  @Test
  public void testDeserializeListFromJSON() throws IOException {
    assertEquals(fromJson(jsonFixture("fixtures/location-list.json"), new TypeReference<List<LocationSummary>>() {}), 
            Collections.singletonList(summary));
  }

}
