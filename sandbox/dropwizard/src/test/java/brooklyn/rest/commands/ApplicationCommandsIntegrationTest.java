package brooklyn.rest.commands;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

import brooklyn.rest.commands.applications.DeleteApplicationCommand;
import brooklyn.rest.commands.applications.ListApplicationsCommand;
import brooklyn.rest.commands.applications.ListEffectorsCommand;
import brooklyn.rest.commands.applications.QuerySensorsCommand;
import brooklyn.rest.commands.applications.StartApplicationCommand;
import brooklyn.rest.resources.ApplicationResource;
import brooklyn.rest.resources.EffectorResource;
import brooklyn.rest.resources.EntityResource;
import brooklyn.rest.resources.PolicyResource;
import brooklyn.rest.resources.SensorResource;

public class ApplicationCommandsIntegrationTest extends BrooklynCommandTest {

    private static final Logger log = LoggerFactory.getLogger(ApplicationCommandsIntegrationTest.class);

  @Override
  protected void setUpResources() throws Exception {
    addResource(new ApplicationResource());
    addResource(new EntityResource());
    addResource(new SensorResource());
    addResource(new EffectorResource());
    addResource(new PolicyResource());
  }
  
  @AfterClass
  public void tearDown() throws Exception {
    super.tearDownJersey();
  }

  @Test(groups="Integration")
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

  @Test(groups="Integration", dependsOnMethods = "testStartApplication")
  public void testListApplications() throws Exception {
    runCommandWithArgs(ListApplicationsCommand.class);

    assertThat(standardOut(), allOf(containsString("redis"), containsString("RUNNING")));
  }

  @Test(groups="Integration", dependsOnMethods = "testStartApplication")
  public void testQuerySensors() throws Exception {
    runCommandWithArgs(QuerySensorsCommand.class, "redis");

    assertThat(standardOut(), allOf(
        containsString("/v1/applications/redis/entities/"),
        containsString("brooklyn.entity.nosql.redis.RedisStore"),
        containsString("redis.port = 700")
    ));
  }

  @Test(groups="Integration", dependsOnMethods = "testStartApplication")
  public void testListEffectors() throws Exception {
    runCommandWithArgs(ListEffectorsCommand.class, "redis");

    assertThat(standardOut(), allOf(
        containsString("/v1/applications/redis/entities/"),
        containsString("void start"),
        containsString("void stop []")
    ));
  }

  @Test(groups="Integration", dependsOnMethods = {"testListEffectors", "testQuerySensors", "testListApplications"})
  public void testDeleteApplication() throws Exception {
    runCommandWithArgs(DeleteApplicationCommand.class, "redis");

    assertThat(standardOut(), containsString("Ok, status: 202"));
  }

}
