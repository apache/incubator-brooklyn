package brooklyn.rest.domain;

import static com.yammer.dropwizard.testing.JsonHelpers.asJson;
import static com.yammer.dropwizard.testing.JsonHelpers.fromJson;
import static com.yammer.dropwizard.testing.JsonHelpers.jsonFixture;
import static org.testng.Assert.assertEquals;

import java.io.IOException;
import java.net.URI;
import java.util.Map;

import org.testng.Assert;
import org.testng.annotations.Test;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.Entities;
import brooklyn.management.ManagementContext;
import brooklyn.test.entity.TestApplicationImpl;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

public class ApplicationTest {

  final EntitySpec entitySpec = new EntitySpec("Vanilla Java App", "brooklyn.entity.java.VanillaJavaApp",
      ImmutableMap.<String, String>of(
          "initialSize", "1",
          "creationScriptUrl", "http://my.brooklyn.io/storage/foo.sql"
      ));

  final ApplicationSpec applicationSpec = ApplicationSpec.builder().name("myapp").
          entities(ImmutableSet.of(entitySpec)).
          locations(ImmutableSet.of("/v1/locations/1")).
          build();

  final ApplicationSummary application = new ApplicationSummary(applicationSpec, Status.STARTING, null, null);

  @Test
  public void testSerializeToJSON() throws IOException {
    ApplicationSummary application1 = new ApplicationSummary(applicationSpec, Status.STARTING, null, null) {
      @Override
      public Map<String, URI> getLinks() {
        return ImmutableMap.of(
            "self", URI.create("/v1/applications/" + applicationSpec.getName()),
            "entities", URI.create("fixtures/entity-summary-list.json")
        );
      }
    };
    assertEquals(asJson(application1), jsonFixture("fixtures/application.json"));
  }

  @Test
  public void testDeserializeFromJSON() throws IOException {
    assertEquals(fromJson(jsonFixture("fixtures/application.json"),
        ApplicationSummary.class), application);
  }

  @Test
  public void testTransitionToRunning() {
        ApplicationSummary running = application.transitionTo(Status.RUNNING);
        assertEquals(running.getStatus(), Status.RUNNING);
  }

  @Test
  public void testAppInAppTest() throws IOException {
      TestApplicationImpl app = new TestApplicationImpl();
      ManagementContext mgmt = Entities.startManagement(app);
      try {
          Entity e2 = app.addChild(new TestApplicationImpl());
          Entities.manage(e2);
          if (mgmt.getApplications().size()!=1)
              Assert.fail("Apps in Apps should not be listed at top level: "+mgmt.getApplications());
      } finally {
          Entities.destroyAll(mgmt);
      }
  }

}
