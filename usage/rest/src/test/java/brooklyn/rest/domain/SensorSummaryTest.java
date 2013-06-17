package brooklyn.rest.domain;

import static com.yammer.dropwizard.testing.JsonHelpers.asJson;
import static com.yammer.dropwizard.testing.JsonHelpers.fromJson;
import static com.yammer.dropwizard.testing.JsonHelpers.jsonFixture;
import static org.testng.Assert.assertEquals;

import java.io.IOException;
import java.net.URI;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.config.render.RendererHints;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.EntitySpecs;
import brooklyn.event.AttributeSensor;
import brooklyn.event.Sensor;
import brooklyn.event.basic.BasicAttributeSensor.StringAttributeSensor;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;

import com.google.common.collect.ImmutableMap;

public class SensorSummaryTest {

  private SensorSummary sensorSummary = new SensorSummary("redis.uptime", "Integer",
      "Description", ImmutableMap.of(
      "self", URI.create("/v1/applications/redis-app/entities/redis-ent/sensors/redis.uptime")));

  private TestApplication app;
  private TestEntity entity;
  
  @BeforeMethod(alwaysRun=true)
  public void setUp() throws Exception {
      app = ApplicationBuilder.newManagedApp(TestApplication.class);
      entity = app.createAndManageChild(EntitySpecs.spec(TestEntity.class));
  }
  
  @AfterMethod(alwaysRun=true)
  public void tearDown() throws Exception {
      if (app != null) Entities.destroy(app);
  }
  
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
      Sensor<String> sensor = new StringAttributeSensor("name with space");
      SensorSummary summary = new SensorSummary(entity, sensor);
      URI selfUri = summary.getLinks().get("self");
      
      String expectedUri = "/v1/applications/" + entity.getApplicationId() + "/entities/" + entity.getId() + "/sensors/" + "name%20with%20space";

      assertEquals(selfUri, URI.create(expectedUri));
  }
  
  // Previously failed because immutable-map builder threw exception if put same key multiple times,
  // and the NamedActionWithUrl did not have equals/hashCode
  @Test
  public void testSensorWithMultipleOpenUrlActionsRegistered() throws IOException {
      AttributeSensor<String> sensor = new StringAttributeSensor("sensor1");
      entity.setAttribute(sensor, "http://myval");
      RendererHints.register(sensor, new RendererHints.NamedActionWithUrl("Open"));
      RendererHints.register(sensor, new RendererHints.NamedActionWithUrl("Open"));

      SensorSummary summary = new SensorSummary(entity, sensor);
      
      assertEquals(summary.getLinks().get("action:open"), URI.create("http://myval"));
  }
}
