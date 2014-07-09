package brooklyn.rest.domain;

import com.google.common.collect.Maps;
import static com.yammer.dropwizard.testing.JsonHelpers.asJson;
import static com.yammer.dropwizard.testing.JsonHelpers.fromJson;
import static com.yammer.dropwizard.testing.JsonHelpers.jsonFixture;
import static org.testng.Assert.assertEquals;
import org.testng.annotations.Test;

import java.io.IOException;
import java.net.URI;
import java.util.Map;

public class EntitySummaryTest {

  static final Map<String, URI> links;
  static {
    links = Maps.newLinkedHashMap();
    links.put("self", URI.create("/v1/applications/tesr/entities/zQsqdXzi"));
    links.put("catalog", URI.create("/v1/catalog/entities/brooklyn.entity.webapp.tomcat.TomcatServer"));
    links.put("application", URI.create("/v1/applications/tesr"));
    links.put("children", URI.create("/v1/applications/tesr/entities/zQsqdXzi/entities"));
    links.put("effectors", URI.create("fixtures/effector-summary-list.json"));
    links.put("sensors", URI.create("fixtures/sensor-summary-list.json"));
    links.put("activities", URI.create("fixtures/task-summary-list.json"));
  }

  static final EntitySummary entitySummary = new EntitySummary(
          "zQsqdXzi", "MyTomcat", "brooklyn.entity.webapp.tomcat.TomcatServer", links);

  @Test
  public void testSerializeToJSON() throws IOException {
    assertEquals(asJson(entitySummary), jsonFixture("fixtures/entity-summary.json"));
  }

  @Test
  public void testDeserializeFromJSON() throws IOException {
    assertEquals(fromJson(jsonFixture("fixtures/entity-summary.json"), EntitySummary.class), entitySummary);
  }

}
