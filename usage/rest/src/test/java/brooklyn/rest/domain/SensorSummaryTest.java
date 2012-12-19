package brooklyn.rest.domain;

import static com.yammer.dropwizard.testing.JsonHelpers.asJson;
import static com.yammer.dropwizard.testing.JsonHelpers.fromJson;
import static com.yammer.dropwizard.testing.JsonHelpers.jsonFixture;
import static org.testng.Assert.assertEquals;

import java.io.IOException;
import java.net.URI;

import org.testng.annotations.Test;

import brooklyn.entity.Application;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractApplication;
import brooklyn.event.Sensor;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.test.entity.TestEntity;

import com.google.common.collect.ImmutableMap;

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
  
  @Test
  public void testEscapesUriForSensorName() throws IOException {
      Application app = new AbstractApplication() {};
      Entity entity = new TestEntity(app);
      Sensor<String> sensor = new BasicAttributeSensor<String>(String.class, "name with space");
      SensorSummary summary = new SensorSummary(entity, sensor);
      URI selfUri = summary.getLinks().get("self");
      
      String expectedUri = "/v1/applications/" + entity.getApplicationId() + "/entities/" + entity.getId() + "/sensors/" + "name%20with%20space";

      assertEquals(selfUri, URI.create(expectedUri));
  }
}
