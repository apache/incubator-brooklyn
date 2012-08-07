package brooklyn.rest.commands;

import brooklyn.rest.BrooklynConfiguration;
import brooklyn.rest.commands.applications.*;
import brooklyn.rest.core.ApplicationManager;
import brooklyn.rest.core.LocationStore;
import brooklyn.rest.resources.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

public class ApplicationCommandsTest extends BrooklynCommandTest {

  private ExecutorService executorService;
  private ApplicationManager manager;

  @Override
  protected void setUpResources() throws Exception {
    executorService = Executors.newCachedThreadPool();
    LocationStore locationStore = LocationStore.withLocalhost();

    manager = new ApplicationManager(new BrooklynConfiguration(), locationStore,
        new CatalogResource(), executorService);

    addResource(new ApplicationResource(manager, locationStore, new CatalogResource()));
    addResource(new EntityResource(manager));
    addResource(new SensorResource(manager));
    addResource(new EffectorResource(manager, executorService));
  }

  @AfterClass
  public void tearDown() throws Exception {
    super.tearDownJersey();
    manager.stop();
    executorService.shutdown();
  }

  @Test
  public void testStartApplication() throws Exception {
    String redisRecipe = "{\"entities\":[\n" +
        "  {\n" +
        "    \"type\":\"brooklyn.entity.nosql.redis.RedisStore\",\n" +
        "    \"config\": {\"redisPort\": \"7000+\"}\n" +
        "  }\n" +
        "],\n" +
        "  \"locations\":[\n" +
        "    \"/v1/locations/0\"\n" +
        "  ],\n" +
        "  \"name\":\"redis\"\n" +
        "}";
    runCommandWithArgs(StartApplicationCommand.class,
        createTemporaryFileWithContent(".json", redisRecipe));

    assertThat(standardOut(), allOf(containsString("/v1/applications/redis"),
        containsString("Done")));
  }

  @Test(dependsOnMethods = "testStartApplication")
  public void testListApplications() throws Exception {
    runCommandWithArgs(ListApplicationsCommand.class);

    assertThat(standardOut(), allOf(containsString("redis"), containsString("RUNNING")));
  }

  @Test(dependsOnMethods = "testStartApplication")
  public void testQuerySensors() throws Exception {
    runCommandWithArgs(QuerySensorsCommand.class, "redis");

    assertThat(standardOut(), allOf(
        containsString("/v1/applications/redis/entities/"),
        containsString("brooklyn.entity.nosql.redis.RedisStore"),
        containsString("redis.port = 700")
    ));
  }

  @Test(dependsOnMethods = "testStartApplication")
  public void testListEffectors() throws Exception {
    runCommandWithArgs(ListEffectorsCommand.class, "redis");

    assertThat(standardOut(), allOf(
        containsString("/v1/applications/redis/entities/"),
        containsString("void start"),
        containsString("void stop []")
    ));
  }

  @Test(dependsOnMethods = {"testListEffectors", "testQuerySensors", "testListApplications"})
  public void testDeleteApplication() throws Exception {
    runCommandWithArgs(DeleteApplicationCommand.class, "redis");

    assertThat(standardOut(), containsString("Ok, status: 202"));
  }

}
