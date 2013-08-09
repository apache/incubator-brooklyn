package brooklyn.rest.resources;

import static com.google.common.collect.Iterables.find;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;

import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

import brooklyn.entity.basic.BasicApplication;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.location.Location;
import brooklyn.location.basic.AbstractLocation;
import brooklyn.location.basic.LocationConfigKeys;
import brooklyn.location.geo.HostGeoInfo;
import brooklyn.rest.domain.ApiError;
import brooklyn.rest.domain.ApplicationSpec;
import brooklyn.rest.domain.ApplicationSummary;
import brooklyn.rest.domain.EffectorSummary;
import brooklyn.rest.domain.EntityConfigSummary;
import brooklyn.rest.domain.EntitySpec;
import brooklyn.rest.domain.EntitySummary;
import brooklyn.rest.domain.PolicySummary;
import brooklyn.rest.domain.SensorSummary;
import brooklyn.rest.domain.TaskSummary;
import brooklyn.rest.testing.BrooklynRestResourceTest;
import brooklyn.rest.testing.mocks.CapitalizePolicy;
import brooklyn.rest.testing.mocks.RestMockApp;
import brooklyn.rest.testing.mocks.RestMockAppBuilder;
import brooklyn.rest.testing.mocks.RestMockSimpleEntity;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.GenericType;
import com.sun.jersey.api.client.UniformInterfaceException;

@Test(singleThreaded = true)
public class ApplicationResourceTest extends BrooklynRestResourceTest {

    private static final Logger log = LoggerFactory.getLogger(ApplicationResourceTest.class);
    
  private final ApplicationSpec simpleSpec = ApplicationSpec.builder().name("simple-app").
          entities(ImmutableSet.of(new EntitySpec("simple-ent", RestMockSimpleEntity.class.getName()))).
          locations(ImmutableSet.of("localhost")).
          build();

  @Override
  protected void setUpResources() throws Exception {
      addResources();
  }

  @AfterClass
  @Override
  public void tearDown() throws Exception {
    super.tearDown();
    stopManager();
  }

  @Test
  public void testGetUndefinedApplication() {
    try {
      client().resource("/v1/applications/dummy-not-found").get(ApplicationSummary.class);
    } catch (UniformInterfaceException e) {
      assertEquals(e.getResponse().getStatus(), 404);
    }
  }

  private static void assertRegexMatches(String actual, String patternExpected) {
      if (actual==null) Assert.fail("Actual value is null; expected "+patternExpected);
      if (!actual.matches(patternExpected)) {
          Assert.fail("Text '"+actual+"' does not match expected pattern "+patternExpected);
      }
  }
  
  @Test
  public void testDeployApplication() throws InterruptedException, TimeoutException {
    ClientResponse response = client().resource("/v1/applications")
        .post(ClientResponse.class, simpleSpec);

    assertEquals(getManagementContext().getApplications().size(), 1);
    assertRegexMatches(response.getLocation().getPath(), "/v1/applications/.*");
//    Object taskO = response.getEntity(Object.class);
    TaskSummary task = response.getEntity(TaskSummary.class);
    log.info("deployed, got "+task);
    assertEquals(task.getEntityId(), 
            getManagementContext().getApplications().iterator().next().getApplicationId());

    waitForApplicationToBeRunning(response.getLocation());
  }

  @Test(dependsOnMethods = {"testDeleteApplication"})
  // this must happen after we've deleted the main applicaiton, as testLocatedLocations assumes a single location
  public void testDeployApplicationImpl() throws Exception {
    ApplicationSpec spec = ApplicationSpec.builder()
            .type(RestMockApp.class.getCanonicalName())
            .name("simple-app-impl")
            .locations(ImmutableSet.of("localhost"))
            .build();
      
    ClientResponse response = client().resource("/v1/applications")
        .post(ClientResponse.class, spec);

    // Expect app to be running
    URI appUri = response.getLocation();
    waitForApplicationToBeRunning(response.getLocation());
    assertEquals(client().resource(appUri).get(ApplicationSummary.class).getSpec().getName(), "simple-app-impl");
  }

  @Test(dependsOnMethods = {"testDeployApplication", "testLocatedLocation"})
  public void testDeployApplicationFromInterface() throws Exception {
    ApplicationSpec spec = ApplicationSpec.builder()
            .type(BasicApplication.class.getCanonicalName())
            .name("simple-app-interface")
            .locations(ImmutableSet.of("localhost"))
            .build();
      
    ClientResponse response = client().resource("/v1/applications")
        .post(ClientResponse.class, spec);

    // Expect app to be running
    URI appUri = response.getLocation();
    waitForApplicationToBeRunning(response.getLocation());
    assertEquals(client().resource(appUri).get(ApplicationSummary.class).getSpec().getName(), "simple-app-interface");
  }

  @Test(dependsOnMethods = {"testDeployApplication", "testLocatedLocation"})
  public void testDeployApplicationFromBuilder() throws Exception {
    ApplicationSpec spec = ApplicationSpec.builder()
            .type(RestMockAppBuilder.class.getCanonicalName())
            .name("simple-app-builder")
            .locations(ImmutableSet.of("localhost"))
            .build();
      
    ClientResponse response = client().resource("/v1/applications")
        .post(ClientResponse.class, spec);

    // Expect app to be running
    URI appUri = response.getLocation();
    waitForApplicationToBeRunning(response.getLocation());
    assertEquals(client().resource(appUri).get(ApplicationSummary.class).getSpec().getName(), "simple-app-builder");
    
    // Expect app to have the child-entity
    Set<EntitySummary> entities = client().resource(appUri.toString() + "/entities")
            .get(new GenericType<Set<EntitySummary>>() {});
    assertEquals(entities.size(), 1);
    assertEquals(Iterables.getOnlyElement(entities).getName(), "child1");
    assertEquals(Iterables.getOnlyElement(entities).getType(), RestMockSimpleEntity.class.getCanonicalName());
  }

  @Test
  public void testDeployWithInvalidEntityType() {
    try {
      client().resource("/v1/applications").post(
          ApplicationSpec.builder().name("invalid-app").
              entities(ImmutableSet.of(new EntitySpec("invalid-ent", "not.existing.entity"))).
              locations(ImmutableSet.of("localhost")).
              build()
      );

    } catch (UniformInterfaceException e) {
      ApiError error = e.getResponse().getEntity(ApiError.class);
      assertEquals(error.getMessage(), "Undefined type 'not.existing.entity'");
    }
  }

  @Test
  public void testDeployWithInvalidLocation() {
    try {
      client().resource("/v1/applications").post(
          ApplicationSpec.builder().name("invalid-app").
              entities(ImmutableSet.<EntitySpec>of(new EntitySpec("simple-ent", RestMockSimpleEntity.class.getName()))).
              locations(ImmutableSet.of("3423")).
              build()
      );

    } catch (UniformInterfaceException e) {
      ApiError error = e.getResponse().getEntity(ApiError.class);
      assertEquals(error.getMessage(), "Undefined location '3423'");
    }
  }

  @Test(dependsOnMethods = "testDeployApplication")
  public void testListEntities() {
    Set<EntitySummary> entities = client().resource("/v1/applications/simple-app/entities")
        .get(new GenericType<Set<EntitySummary>>() {
        });

    assertEquals(entities.size(), 1);
    
    for (EntitySummary entity : entities) {
      client().resource(entity.getLinks().get("self")).get(ClientResponse.class);

      Set<EntitySummary> children = client().resource(entity.getLinks().get("children"))
          .get(new GenericType<Set<EntitySummary>>() {
          });
      assertEquals(children.size(), 0);
    }
  }

  @Test(dependsOnMethods = "testDeployApplication")
  public void testListApplications() {
    Set<ApplicationSummary> applications = client().resource("/v1/applications")
        .get(new GenericType<Set<ApplicationSummary>>() {
        });
    log.info("Applications are: "+applications);
    for (ApplicationSummary app: applications) {
        if (simpleSpec.getName().equals(app.getSpec().getName())) return;
    }
    Assert.fail("simple-app not found in list of applications: "+applications);
  }

  @Test(dependsOnMethods = "testDeployApplication")
  public void testListSensors() {
    Set<SensorSummary> sensors = client().resource("/v1/applications/simple-app/entities/simple-ent/sensors")
        .get(new GenericType<Set<SensorSummary>>() {
        });
    assertTrue(sensors.size() > 0);
    SensorSummary sample = Iterables.find(sensors, new Predicate<SensorSummary>() {
      @Override
      public boolean apply(SensorSummary sensorSummary) {
        return sensorSummary.getName().equals(RestMockSimpleEntity.SAMPLE_SENSOR.getName());
      }
    });
    assertEquals(sample.getType(), "java.lang.String");
  }

  @Test(dependsOnMethods = "testDeployApplication")
  public void testListConfig() {
    Set<EntityConfigSummary> config = client().resource("/v1/applications/simple-app/entities/simple-ent/config")
        .get(new GenericType<Set<EntityConfigSummary>>() {
        });
    assertTrue(config.size() > 0);
    System.out.println(("CONFIG: "+config));
  }
  
  @Test(dependsOnMethods = "testListConfig")
  public void testListConfig2() {
    Set<EntityConfigSummary> config = client().resource("/v1/applications/simple-app/entities/simple-ent/config")
        .get(new GenericType<Set<EntityConfigSummary>>() {
        });
    assertTrue(config.size() > 0);
    System.out.println(("CONFIG: "+config));
  }
  
  @Test(dependsOnMethods = "testDeployApplication")
  public void testListEffectors() {
    Set<EffectorSummary> effectors = client().resource("/v1/applications/simple-app/entities/simple-ent/effectors")
        .get(new GenericType<Set<EffectorSummary>>() {
        });

    assertTrue(effectors.size() > 0);

    EffectorSummary sampleEffector = find(effectors, new Predicate<EffectorSummary>() {
      @Override
      public boolean apply(EffectorSummary input) {
        return input.getName().equals("sampleEffector");
      }
    });
    assertEquals(sampleEffector.getReturnType(), "java.lang.String");
  }

  @Test(dependsOnMethods = "testListSensors")
  public void testTriggerSampleEffector() throws InterruptedException, IOException {
    ClientResponse response = client().resource("/v1/applications/simple-app/entities/simple-ent/effectors/"+
            RestMockSimpleEntity.SAMPLE_EFFECTOR.getName())
        .post(ClientResponse.class, ImmutableMap.of("param1", "foo", "param2", 4));

    assertEquals(response.getStatus(), Response.Status.ACCEPTED.getStatusCode());
    
    String result = response.getEntity(String.class);
    assertEquals(result, "foo4");
  }

  @Test(dependsOnMethods = "testTriggerSampleEffector")
  public void testBatchSensorValues() {
    Map<String,String> sensors = client().resource("/v1/applications/simple-app/entities/simple-ent/sensors/current-state")
        .get(new GenericType<Map<String,String>>() {});
    assertTrue(sensors.size() > 0);
    assertEquals(sensors.get(RestMockSimpleEntity.SAMPLE_SENSOR.getName()), "foo4");
  }

  @Test(dependsOnMethods = "testBatchSensorValues")
  public void testReadEachSensor() {
    Set<SensorSummary> sensors = client().resource("/v1/applications/simple-app/entities/simple-ent/sensors")
        .get(new GenericType<Set<SensorSummary>>() {
        });

    Map<String, String> readings = Maps.newHashMap();
    for (SensorSummary sensor : sensors) {
      readings.put(sensor.getName(), client().resource(sensor.getLinks().get("self")).get(String.class));
    }

    assertEquals(readings.get(RestMockSimpleEntity.SAMPLE_SENSOR.getName()), "foo4");
  }

  @Test(dependsOnMethods = "testTriggerSampleEffector")
  public void testPolicyWhichCapitalizes() {
      String policiesEndpoint = "/v1/applications/simple-app/entities/simple-ent/policies";
      Set<PolicySummary> policies = client().resource(policiesEndpoint).get(new GenericType<Set<PolicySummary>>(){});
      assertEquals(policies.size(), 0);
      
      ClientResponse response = client().resource(policiesEndpoint).
          queryParam("type", CapitalizePolicy.class.getCanonicalName()).
          post(ClientResponse.class, Maps.newHashMap());
      assertEquals(response.getStatus(), 200);
      String newPolicyId = response.getEntity(String.class);
      log.info("POLICY CREATED: "+newPolicyId);
      policies = client().resource(policiesEndpoint).get(new GenericType<Set<PolicySummary>>(){});
      assertEquals(policies.size(), 1);
      
      String status = client().resource(policiesEndpoint+"/"+newPolicyId).
          get(String.class);
      log.info("POLICY STATUS: "+status);
      
      response = client().resource(policiesEndpoint+"/"+newPolicyId+"/start").
              post(ClientResponse.class);
      assertEquals(response.getStatus(), 200);
      status = client().resource(policiesEndpoint+"/"+newPolicyId).
              get(String.class);
      assertEquals(status, Lifecycle.RUNNING.name());
      
      response = client().resource(policiesEndpoint+"/"+newPolicyId+"/stop").
              post(ClientResponse.class);
      assertEquals(response.getStatus(), 200);
      status = client().resource(policiesEndpoint+"/"+newPolicyId).
              get(String.class);
      assertEquals(status, Lifecycle.STOPPED.name());
      
      response = client().resource(policiesEndpoint+"/"+newPolicyId+"/destroy").
              post(ClientResponse.class);
      assertTrue(response.getStatus()==200 || response.getStatus()==404);
      response = client().resource(policiesEndpoint+"/"+newPolicyId).get(ClientResponse.class);
      log.info("POLICY STATUS RESPONSE AFTER DESTROY: "+response.getStatus());
      assertTrue(response.getStatus()==200 || response.getStatus()==404);
      if (response.getStatus()==200) {
          assertEquals(response.getEntity(String.class), Lifecycle.DESTROYED.name());
      }
      
      policies = client().resource(policiesEndpoint).get(new GenericType<Set<PolicySummary>>(){});
      assertEquals(0, policies.size());      
  }

  @SuppressWarnings({ "rawtypes" })
  @Test(dependsOnMethods = "testDeployApplication")
  public void testLocatedLocation() {
      log.info("starting testLocatedLocations");
      testListApplications();
      
    Location l = getManagementContext().getApplications().iterator().next().getLocations().iterator().next();
    if (!l.hasConfig(LocationConfigKeys.LATITUDE, false)) {
        log.info("Supplying fake locations for localhost because could not be autodetected");
        ((AbstractLocation)l).setHostGeoInfo(new HostGeoInfo("localhost", "localhost", 50, 0));
    }
    Map result = client().resource("/v1/locations/usage/LocatedLocations")
        .get(Map.class);
    log.info("LOCATIONS: "+result);
    Assert.assertEquals(result.size(), 1);
    Map details = (Map) result.values().iterator().next();
    assertEquals(details.get("leafEntityCount"), 1);
  }

  @Test(dependsOnMethods = {"testListEffectors", "testTriggerSampleEffector", "testListApplications","testReadEachSensor","testPolicyWhichCapitalizes","testLocatedLocation"})
  public void testDeleteApplication() throws TimeoutException, InterruptedException {
    int size = getManagementContext().getApplications().size();
    ClientResponse response = client().resource("/v1/applications/simple-app")
        .delete(ClientResponse.class);

    assertEquals(response.getStatus(), Response.Status.ACCEPTED.getStatusCode());
    TaskSummary task = response.getEntity(TaskSummary.class);
    assertTrue(task.getDescription().toLowerCase().contains("destroy"), task.getDescription());
    assertTrue(task.getDescription().toLowerCase().contains("simple-app"), task.getDescription());
    
    waitForPageNotFoundResponse("/v1/applications/simple-app", ApplicationSummary.class);

    assertEquals(getManagementContext().getApplications().size(), size-1);
  }
  
}
