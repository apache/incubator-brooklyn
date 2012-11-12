package brooklyn.rest.resources;

import static com.google.common.collect.Iterables.find;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;

import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

import brooklyn.entity.basic.Lifecycle;
import brooklyn.rest.BaseResourceTest;
import brooklyn.rest.BrooklynConfiguration;
import brooklyn.rest.api.ApiError;
import brooklyn.rest.api.Application;
import brooklyn.rest.api.ApplicationSpec;
import brooklyn.rest.api.ConfigSummary;
import brooklyn.rest.api.EffectorSummary;
import brooklyn.rest.api.EntitySpec;
import brooklyn.rest.api.EntitySummary;
import brooklyn.rest.api.PolicySummary;
import brooklyn.rest.api.SensorSummary;
import brooklyn.rest.core.ApplicationManager;
import brooklyn.rest.core.LocationStore;
import brooklyn.rest.mock.CapitalizePolicy;
import brooklyn.rest.mock.RestMockSimpleEntity;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.GenericType;
import com.sun.jersey.api.client.UniformInterfaceException;

@Test(singleThreaded = true)
public class ApplicationResourceTest extends BaseResourceTest {

    private static final Logger log = LoggerFactory.getLogger(ApplicationResourceTest.class);
    
  private ApplicationManager manager;
  private ExecutorService executorService;

  private final ApplicationSpec simpleSpec = ApplicationSpec.builder().name("simple-app").
          entities(ImmutableSet.of(new EntitySpec("simple-ent", RestMockSimpleEntity.class.getName()))).
          locations(ImmutableSet.of("/v1/locations/0")).
          build();

  @Override
  protected void setUpResources() throws Exception {
    executorService = Executors.newCachedThreadPool();
    LocationStore locationStore = LocationStore.withLocalhost();

    manager = new ApplicationManager(new BrooklynConfiguration(), locationStore,
        new CatalogResource(), executorService);

    addResource(new ApplicationResource(manager, locationStore, new CatalogResource()));
    addResource(new EntityResource(manager));
    addResource(new ConfigResource(manager));
    addResource(new SensorResource(manager));
    addResource(new EffectorResource(manager));
    addResource(new PolicyResource(manager));
    addResource(new ActivityResource(manager));
  }

  @AfterClass
  @Override
  public void tearDown() throws Exception {
    super.tearDown();
    manager.stop();
    executorService.shutdown();
  }

  @Test
  public void testGetUndefinedApplication() {
    try {
      client().resource("/v1/applications/dummy-not-found").get(Application.class);
    } catch (UniformInterfaceException e) {
      assertEquals(e.getResponse().getStatus(), 404);
    }
  }

  @Test
  public void testDeployApplication() throws InterruptedException, TimeoutException {
    ClientResponse response = client().resource("/v1/applications")
        .post(ClientResponse.class, simpleSpec);

    assertEquals(manager.registryById().size(), 1);
    assertEquals(response.getLocation().getPath(), "/v1/applications/simple-app");
    assertEquals(response.getEntity(String.class), manager.registryById().keySet().iterator().next());

    waitForApplicationToBeRunning(response.getLocation());
  }

  @Test
  public void testDeployWithInvalidEntityType() {
    try {
      client().resource("/v1/applications").post(
          ApplicationSpec.builder().name("invalid-app").
              entities(ImmutableSet.of(new EntitySpec("invalid-ent", "not.existing.entity"))).
              locations(ImmutableSet.<String>of("/v1/locations/0")).
              build()
      );

    } catch (UniformInterfaceException e) {
      ApiError error = e.getResponse().getEntity(ApiError.class);
      assertEquals(error.getMessage(), "Undefined entity type 'not.existing.entity'");
    }
  }

  @Test
  public void testDeployWithInvalidLocation() {
    try {
      client().resource("/v1/applications").post(
          ApplicationSpec.builder().name("invalid-app").
              entities(ImmutableSet.<EntitySpec>of(new EntitySpec("simple-ent", RestMockSimpleEntity.class.getName()))).
              locations(ImmutableSet.of("/v1/locations/3423")).
              build()
      );

    } catch (UniformInterfaceException e) {
      ApiError error = e.getResponse().getEntity(ApiError.class);
      assertEquals(error.getMessage(), "Undefined location '/v1/locations/3423'");
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
    Set<Application> applications = client().resource("/v1/applications")
        .get(new GenericType<Set<Application>>() {
        });
    for (Application app: applications) {
        if (app.getSpec().equals(simpleSpec)) return;
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
    Set<ConfigSummary> config = client().resource("/v1/applications/simple-app/entities/simple-ent/config")
        .get(new GenericType<Set<ConfigSummary>>() {
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

  @Test(dependsOnMethods = {"testListEffectors", "testTriggerSampleEffector", "testListApplications","testReadEachSensor","testPolicyWhichCapitalizes"})
  public void testDeleteApplication() throws TimeoutException, InterruptedException {
    int size = manager.registryById().size();
    ClientResponse response = client().resource("/v1/applications/simple-app")
        .delete(ClientResponse.class);

    waitForPageNotFoundResponse("/v1/applications/simple-app", Application.class);

    assertEquals(response.getStatus(), Response.Status.ACCEPTED.getStatusCode());
    assertEquals(manager.registryById().size(), size-1);
  }
  
}
