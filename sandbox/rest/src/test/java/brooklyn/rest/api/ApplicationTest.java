package brooklyn.rest.api;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import static com.yammer.dropwizard.testing.JsonHelpers.asJson;
import static com.yammer.dropwizard.testing.JsonHelpers.fromJson;
import static com.yammer.dropwizard.testing.JsonHelpers.jsonFixture;
import java.io.IOException;
import static org.testng.Assert.assertEquals;
import org.testng.annotations.Test;

public class ApplicationTest {
  final Entity entity = new Entity("brooklyn.entity.java.VanillaJavaApp", ImmutableMap.<String, String>of(
      "initialSize", "1",
      "creationScriptUrl", "http://my.brooklyn.io/storage/foo.sql"
  ));
  final Application application = new Application(ImmutableSet.of(entity));

  @Test
  public void testSerializeToJSON() throws IOException {
    assertEquals(asJson(application), jsonFixture("fixtures/application.json"));
  }

  @Test
  public void testDeserializeFromJSON() throws IOException {
    assertEquals(fromJson(jsonFixture("fixtures/application.json"), Application.class), application);
  }

}
