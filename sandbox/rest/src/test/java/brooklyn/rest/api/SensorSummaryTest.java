package brooklyn.rest.api;

import com.google.common.collect.ImmutableMap;
import static com.yammer.dropwizard.testing.JsonHelpers.asJson;
import static com.yammer.dropwizard.testing.JsonHelpers.fromJson;
import static com.yammer.dropwizard.testing.JsonHelpers.jsonFixture;
import java.io.IOException;
import java.net.URI;
import static org.testng.Assert.assertEquals;
import org.testng.annotations.Test;

public class SensorSummaryTest {

  private SensorSummary sensorSummary = new SensorSummary("redis.uptime", "Integer",
      "Description", ImmutableMap.of(
      "self", URI.create("/v1/applications/redis-app/entities/redis-ent/sensors/redis.uptime")));

  @Test
  public void testSerializeToJSON() throws IOException {
    assertEquals(asJson(sensorSummary), jsonFixture("fixtures/sensor-summary.json"));
  }

  @Test
  public void testDeserializeFromJSON() throws IOException {
    assertEquals(fromJson(jsonFixture("fixtures/sensor-summary.json"), SensorSummary.class), sensorSummary);
  }
}
