package brooklyn.rest.commands;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

import brooklyn.rest.BrooklynConfiguration;
import brooklyn.rest.commands.applications.DeleteApplicationCommand;
import brooklyn.rest.commands.applications.InvokeEffectorCommand;
import brooklyn.rest.commands.applications.ListApplicationsCommand;
import brooklyn.rest.commands.applications.ListEffectorsCommand;
import brooklyn.rest.commands.applications.QuerySensorsCommand;
import brooklyn.rest.commands.applications.StartApplicationCommand;
import brooklyn.rest.core.ApplicationManager;
import brooklyn.rest.core.LocationStore;
import brooklyn.rest.mock.RestMockSimpleEntity;
import brooklyn.rest.resources.ApplicationResource;
import brooklyn.rest.resources.CatalogResource;
import brooklyn.rest.resources.EffectorResource;
import brooklyn.rest.resources.EntityResource;
import brooklyn.rest.resources.SensorResource;

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
    addResource(new EffectorResource(manager));
  }

  @AfterClass
  public void tearDown() throws Exception {
    super.tearDownJersey();
    manager.stop();
    executorService.shutdown();
  }

  @Test
  public void testStartApplication() throws Exception {
    String simpleRecipe = "{\"entities\":[\n" +
        "  {\n" +
        "    \"type\":\""+RestMockSimpleEntity.class.getName()+"\",\n" +
        "    \"config\": {\"sampleConfig\": \"hiya-world\"}\n" +
        "  }\n" +
        "],\n" +
        "  \"locations\":[\n" +
        "    \"/v1/locations/0\"\n" +
        "  ],\n" +
        "  \"name\":\"simple\"\n" +
        "}";
    runCommandWithArgs(StartApplicationCommand.class,
        createTemporaryFileWithContent(".json", simpleRecipe));

    assertThat(standardOut(), allOf(containsString("/v1/applications/simple"),
        containsString("Done")));
  }

  @Test(dependsOnMethods = "testStartApplication")
  public void testListApplications() throws Exception {
    runCommandWithArgs(ListApplicationsCommand.class);

    assertThat(standardOut(), allOf(containsString("simple"), containsString("RUNNING")));
  }

  @Test(dependsOnMethods = "testStartApplication")
  public void testListEffectors() throws Exception {
    runCommandWithArgs(ListEffectorsCommand.class, "simple");

    assertThat(standardOut(), allOf(
        containsString("/v1/applications/simple/entities/"),
        containsString("sampleEffector")
    ));
  }

  // TODO CLI to invoke effector with arguments
//  @Test(dependsOnMethods = "testStartApplication")
//  public void testInvokeEffectors() throws Exception {
//    runCommandWithArgs(InvokeEffectorCommand.class, "simple", "sampleEffector", "foo", "2");
//
//    assertThat(standardOut(), allOf(
//        containsString("foo2")
//    ));
//  }
//
//  @Test(dependsOnMethods = "testInvokeEffectors")
//  public void testQuerySensors() throws Exception {
//      runCommandWithArgs(QuerySensorsCommand.class, "simple");
//      
//      assertThat(standardOut(), allOf(
//              containsString("/v1/applications/simple/entities/"),
//              containsString(RestMockSimpleEntity.class.getName()),
//              containsString("foo2")
//              ));
//  }
  
//  @Test(dependsOnMethods = {"testListEffectors", "testQuerySensors", "testListApplications"})
  @Test(dependsOnMethods = {"testListEffectors", "testListApplications"})
  public void testDeleteApplication() throws Exception {
    runCommandWithArgs(DeleteApplicationCommand.class, "simple");

    assertThat(standardOut(), containsString("Ok, status: 202"));
  }

}
