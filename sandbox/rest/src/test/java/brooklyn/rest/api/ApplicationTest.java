package brooklyn.rest.api;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import static com.yammer.dropwizard.testing.JsonHelpers.asJson;
import static com.yammer.dropwizard.testing.JsonHelpers.fromJson;
import static com.yammer.dropwizard.testing.JsonHelpers.jsonFixture;
import static org.testng.Assert.assertEquals;
import org.testng.annotations.Test;

import java.io.IOException;
import java.net.URI;
import java.util.Map;

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

  final Application application = new Application(applicationSpec, Application.Status.STARTING);

  @Test
  public void testSerializeToJSON() throws IOException {
    Application application1 = new Application(applicationSpec, Application.Status.STARTING) {
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
        Application.class), application);
  }

  @Test
  public void testTransitionToRunning() {
    Application running = application.transitionTo(Application.Status.RUNNING);
    assertEquals(running.getStatus(), Application.Status.RUNNING);
  }

}
