/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.brooklyn.rest.resources;

import static com.google.common.collect.Iterables.find;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;

import org.apache.brooklyn.api.entity.Application;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.EntityFunctions;
import org.apache.brooklyn.core.entity.EntityPredicates;
import org.apache.brooklyn.core.entity.lifecycle.Lifecycle;
import org.apache.brooklyn.core.location.AbstractLocation;
import org.apache.brooklyn.core.location.LocationConfigKeys;
import org.apache.brooklyn.core.location.geo.HostGeoInfo;
import org.apache.brooklyn.core.location.internal.LocationInternal;
import org.apache.brooklyn.entity.stock.BasicApplication;
import org.apache.brooklyn.entity.stock.BasicEntity;
import org.apache.brooklyn.rest.domain.ApiError;
import org.apache.brooklyn.rest.domain.ApplicationSpec;
import org.apache.brooklyn.rest.domain.ApplicationSummary;
import org.apache.brooklyn.rest.domain.CatalogEntitySummary;
import org.apache.brooklyn.rest.domain.CatalogItemSummary;
import org.apache.brooklyn.rest.domain.EffectorSummary;
import org.apache.brooklyn.rest.domain.EntityConfigSummary;
import org.apache.brooklyn.rest.domain.EntitySpec;
import org.apache.brooklyn.rest.domain.EntitySummary;
import org.apache.brooklyn.rest.domain.PolicySummary;
import org.apache.brooklyn.rest.domain.SensorSummary;
import org.apache.brooklyn.rest.domain.Status;
import org.apache.brooklyn.rest.domain.TaskSummary;
import org.apache.brooklyn.rest.testing.BrooklynRestResourceTest;
import org.apache.brooklyn.rest.testing.mocks.CapitalizePolicy;
import org.apache.brooklyn.rest.testing.mocks.NameMatcherGroup;
import org.apache.brooklyn.rest.testing.mocks.RestMockApp;
import org.apache.brooklyn.rest.testing.mocks.RestMockAppBuilder;
import org.apache.brooklyn.rest.testing.mocks.RestMockSimpleEntity;
import org.apache.brooklyn.test.Asserts;
import org.apache.brooklyn.util.collections.CollectionFunctionals;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.http.HttpAsserts;
import org.apache.brooklyn.util.time.Duration;
import org.apache.http.HttpHeaders;
import org.apache.http.entity.ContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.GenericType;
import com.sun.jersey.api.client.UniformInterfaceException;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.core.util.MultivaluedMapImpl;

@Test(singleThreaded = true)
public class ApplicationResourceTest extends BrooklynRestResourceTest {

    /*
     * In simpleSpec, not using EverythingGroup because caused problems! The group is a child of the
     * app, and the app is a member of the group. It failed in jenkins with:
     *   BasicApplicationImpl{id=GSPjBCe4} GSPjBCe4
     *     service.isUp: true
     *     service.problems: {service-lifecycle-indicators-from-children-and-members=Required entity not healthy: EverythingGroupImpl{id=KQ4mSEOJ}}
     *     service.state: on-fire
     *     service.state.expected: running @ 1412003485617 / Mon Sep 29 15:11:25 UTC 2014
     *   EverythingGroupImpl{id=KQ4mSEOJ} KQ4mSEOJ
     *     service.isUp: true
     *     service.problems: {service-lifecycle-indicators-from-children-and-members=Required entities not healthy: BasicApplicationImpl{id=GSPjBCe4}, EverythingGroupImpl{id=KQ4mSEOJ}}
     *     service.state: on-fire
     * I'm guessing there's a race: the app was not yet healthy because EverythingGroup hadn't set itself to running; 
     * but then the EverythingGroup would never transition to healthy because one of its members was not healthy.
     */

    private static final Logger log = LoggerFactory.getLogger(ApplicationResourceTest.class);
    
    private final ApplicationSpec simpleSpec = ApplicationSpec.builder().name("simple-app")
          .entities(ImmutableSet.of(
                  new EntitySpec("simple-ent", RestMockSimpleEntity.class.getName()),
                  new EntitySpec("simple-group", NameMatcherGroup.class.getName(), ImmutableMap.of("namematchergroup.regex", "simple-ent"))
          ))
          .locations(ImmutableSet.of("localhost"))
          .build();

    // Convenience for finding an EntitySummary within a collection, based on its name
    private static Predicate<EntitySummary> withName(final String name) {
        return new Predicate<EntitySummary>() {
            public boolean apply(EntitySummary input) {
                return name.equals(input.getName());
            }
        };
    }

    // Convenience for finding a Map within a collection, based on the value of one of its keys
    private static Predicate<? super Map<?,?>> withValueForKey(final Object key, final Object value) {
        return new Predicate<Object>() {
            public boolean apply(Object input) {
                if (!(input instanceof Map)) return false;
                return value.equals(((Map<?, ?>) input).get(key));
            }
        };
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
    public void testDeployApplication() throws Exception {
        ClientResponse response = clientDeploy(simpleSpec);

        HttpAsserts.assertHealthyStatusCode(response.getStatus());
        assertEquals(getManagementContext().getApplications().size(), 1);
        assertRegexMatches(response.getLocation().getPath(), "/v1/applications/.*");
        // Object taskO = response.getEntity(Object.class);
        TaskSummary task = response.getEntity(TaskSummary.class);
        log.info("deployed, got " + task);
        assertEquals(task.getEntityId(), getManagementContext().getApplications().iterator().next().getApplicationId());

        waitForApplicationToBeRunning(response.getLocation());
    }

    @Test(dependsOnMethods = { "testDeleteApplication" })
    // this must happen after we've deleted the main application, as testLocatedLocations assumes a single location
    public void testDeployApplicationImpl() throws Exception {
    ApplicationSpec spec = ApplicationSpec.builder()
            .type(RestMockApp.class.getCanonicalName())
            .name("simple-app-impl")
            .locations(ImmutableSet.of("localhost"))
            .build();

        ClientResponse response = clientDeploy(spec);
        assertTrue(response.getStatus() / 100 == 2, "response is " + response);

        // Expect app to be running
        URI appUri = response.getLocation();
        waitForApplicationToBeRunning(response.getLocation());
        assertEquals(client().resource(appUri).get(ApplicationSummary.class).getSpec().getName(), "simple-app-impl");
    }

    @Test(dependsOnMethods = { "testDeployApplication", "testLocatedLocation" })
    public void testDeployApplicationFromInterface() throws Exception {
        ApplicationSpec spec = ApplicationSpec.builder()
                .type(BasicApplication.class.getCanonicalName())
                .name("simple-app-interface")
                .locations(ImmutableSet.of("localhost"))
                .build();

        ClientResponse response = clientDeploy(spec);
        assertTrue(response.getStatus() / 100 == 2, "response is " + response);

        // Expect app to be running
        URI appUri = response.getLocation();
        waitForApplicationToBeRunning(response.getLocation());
        assertEquals(client().resource(appUri).get(ApplicationSummary.class).getSpec().getName(), "simple-app-interface");
    }

    @Test(dependsOnMethods = { "testDeployApplication", "testLocatedLocation" })
    public void testDeployApplicationFromBuilder() throws Exception {
        ApplicationSpec spec = ApplicationSpec.builder()
                .type(RestMockAppBuilder.class.getCanonicalName())
                .name("simple-app-builder")
                .locations(ImmutableSet.of("localhost"))
                .build();

        ClientResponse response = clientDeploy(spec);
        assertTrue(response.getStatus() / 100 == 2, "response is " + response);

        // Expect app to be running
        URI appUri = response.getLocation();
        waitForApplicationToBeRunning(response.getLocation(), Duration.TEN_SECONDS);
        assertEquals(client().resource(appUri).get(ApplicationSummary.class).getSpec().getName(), "simple-app-builder");

        // Expect app to have the child-entity
        Set<EntitySummary> entities = client().resource(appUri.toString() + "/entities")
                .get(new GenericType<Set<EntitySummary>>() {});
        assertEquals(entities.size(), 1);
        assertEquals(Iterables.getOnlyElement(entities).getName(), "child1");
        assertEquals(Iterables.getOnlyElement(entities).getType(), RestMockSimpleEntity.class.getCanonicalName());
    }

    @Test(dependsOnMethods = { "testDeployApplication", "testLocatedLocation" })
    public void testDeployApplicationYaml() throws Exception {
        String yaml = "{ name: simple-app-yaml, location: localhost, services: [ { serviceType: "+BasicApplication.class.getCanonicalName()+" } ] }";

        ClientResponse response = client().resource("/v1/applications")
                .entity(yaml, "application/x-yaml")
                .post(ClientResponse.class);
        assertTrue(response.getStatus()/100 == 2, "response is "+response);

        // Expect app to be running
        URI appUri = response.getLocation();
        waitForApplicationToBeRunning(response.getLocation());
        assertEquals(client().resource(appUri).get(ApplicationSummary.class).getSpec().getName(), "simple-app-yaml");
    }

    @Test
    public void testReferenceCatalogEntity() throws Exception {
        getManagementContext().getCatalog().addItems("{ name: "+BasicEntity.class.getName()+", "
            + "services: [ { type: "+BasicEntity.class.getName()+" } ] }");

        String yaml = "{ name: simple-app-yaml, location: localhost, services: [ { type: " + BasicEntity.class.getName() + " } ] }";

        ClientResponse response = client().resource("/v1/applications")
                .entity(yaml, "application/x-yaml")
                .post(ClientResponse.class);
        assertTrue(response.getStatus()/100 == 2, "response is "+response);

        // Expect app to be running
        URI appUri = response.getLocation();
        waitForApplicationToBeRunning(response.getLocation());
        assertEquals(client().resource(appUri).get(ApplicationSummary.class).getSpec().getName(), "simple-app-yaml");

        ClientResponse response2 = client().resource(appUri.getPath())
                .delete(ClientResponse.class);
        assertEquals(response2.getStatus(), Response.Status.ACCEPTED.getStatusCode());
    }

    @Test
    public void testDeployWithInvalidEntityType() {
        try {
            clientDeploy(ApplicationSpec.builder()
                    .name("invalid-app")
                    .entities(ImmutableSet.of(new EntitySpec("invalid-ent", "not.existing.entity")))
                    .locations(ImmutableSet.of("localhost"))
                    .build());

        } catch (UniformInterfaceException e) {
            ApiError error = e.getResponse().getEntity(ApiError.class);
            assertEquals(error.getMessage(), "Undefined type 'not.existing.entity'");
        }
    }

    @Test
    public void testDeployWithInvalidLocation() {
        try {
            clientDeploy(ApplicationSpec.builder()
                    .name("invalid-app")
                    .entities(ImmutableSet.of(new EntitySpec("simple-ent", RestMockSimpleEntity.class.getName())))
                    .locations(ImmutableSet.of("3423"))
                    .build());

        } catch (UniformInterfaceException e) {
            ApiError error = e.getResponse().getEntity(ApiError.class);
            assertEquals(error.getMessage(), "Undefined location '3423'");
        }
    }

    @Test(dependsOnMethods = "testDeployApplication")
    public void testListEntities() {
        Set<EntitySummary> entities = client().resource("/v1/applications/simple-app/entities")
                .get(new GenericType<Set<EntitySummary>>() {});

        assertEquals(entities.size(), 2);

        EntitySummary entity = Iterables.find(entities, withName("simple-ent"), null);
        EntitySummary group = Iterables.find(entities, withName("simple-group"), null);
        Assert.assertNotNull(entity);
        Assert.assertNotNull(group);

        client().resource(entity.getLinks().get("self")).get(ClientResponse.class);

        Set<EntitySummary> children = client().resource(entity.getLinks().get("children"))
                .get(new GenericType<Set<EntitySummary>>() {});
        assertEquals(children.size(), 0);
    }

    @Test(dependsOnMethods = "testDeployApplication")
    public void testListApplications() {
        Set<ApplicationSummary> applications = client().resource("/v1/applications")
                .get(new GenericType<Set<ApplicationSummary>>() { });
        log.info("Applications listed are: " + applications);
        for (ApplicationSummary app : applications) {
            if (simpleSpec.getName().equals(app.getSpec().getName())) return;
        }
        Assert.fail("simple-app not found in list of applications: "+applications);
    }

    @Test(dependsOnMethods = "testDeployApplication")
    public void testGetApplicationOnFire() {
        Application app = Iterables.find(manager.getApplications(), EntityPredicates.displayNameEqualTo(simpleSpec.getName()));
        Lifecycle origState = app.getAttribute(Attributes.SERVICE_STATE_ACTUAL);
        
        ApplicationSummary summary = client().resource("/v1/applications/"+app.getId())
                .get(ApplicationSummary.class);
        assertEquals(summary.getStatus(), Status.RUNNING);

        app.sensors().set(Attributes.SERVICE_STATE_ACTUAL, Lifecycle.ON_FIRE);
        try {
            ApplicationSummary summary2 = client().resource("/v1/applications/"+app.getId())
                    .get(ApplicationSummary.class);
            log.info("Application: " + summary2);
            assertEquals(summary2.getStatus(), Status.ERROR);
            
        } finally {
            app.sensors().set(Attributes.SERVICE_STATE_ACTUAL, origState);
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Test(dependsOnMethods = "testDeployApplication")
    public void testFetchApplicationsAndEntity() {
        Collection apps = client().resource("/v1/applications/fetch").get(Collection.class);
        log.info("Applications fetched are: " + apps);

        Map app = null;
        for (Object appI : apps) {
            Object name = ((Map) appI).get("name");
            if ("simple-app".equals(name)) {
                app = (Map) appI;
            }
            if (ImmutableSet.of("simple-ent", "simple-group").contains(name))
                Assert.fail(name + " should not be listed at high level: " + apps);
        }

        Assert.assertNotNull(app);
        Collection children = (Collection) app.get("children");
        Assert.assertEquals(children.size(), 2);

        Map entitySummary = (Map) Iterables.find(children, withValueForKey("name", "simple-ent"), null);
        Map groupSummary = (Map) Iterables.find(children, withValueForKey("name", "simple-group"), null);
        Assert.assertNotNull(entitySummary);
        Assert.assertNotNull(groupSummary);

        String itemIds = app.get("id") + "," + entitySummary.get("id") + "," + groupSummary.get("id");
        Collection entities = client().resource("/v1/applications/fetch?items="+itemIds)
                .get(Collection.class);
        log.info("Applications+Entities fetched are: " + entities);

        Assert.assertEquals(entities.size(), apps.size() + 2);
        Map entityDetails = (Map) Iterables.find(entities, withValueForKey("name", "simple-ent"), null);
        Map groupDetails = (Map) Iterables.find(entities, withValueForKey("name", "simple-group"), null);
        Assert.assertNotNull(entityDetails);
        Assert.assertNotNull(groupDetails);

        Assert.assertEquals(entityDetails.get("parentId"), app.get("id"));
        Assert.assertNull(entityDetails.get("children"));
        Assert.assertEquals(groupDetails.get("parentId"), app.get("id"));
        Assert.assertNull(groupDetails.get("children"));

        Collection entityGroupIds = (Collection) entityDetails.get("groupIds");
        Assert.assertNotNull(entityGroupIds);
        Assert.assertEquals(entityGroupIds.size(), 1);
        Assert.assertEquals(entityGroupIds.iterator().next(), groupDetails.get("id"));

        Collection groupMembers = (Collection) groupDetails.get("members");
        Assert.assertNotNull(groupMembers);

        for (Application appi : getManagementContext().getApplications()) {
            Entities.dumpInfo(appi);
        }
        log.info("MEMBERS: " + groupMembers);

        Assert.assertEquals(groupMembers.size(), 1);
        Map entityMemberDetails = (Map) Iterables.find(groupMembers, withValueForKey("name", "simple-ent"), null);
        Assert.assertNotNull(entityMemberDetails);
        Assert.assertEquals(entityMemberDetails.get("id"), entityDetails.get("id"));
    }

    @Test(dependsOnMethods = "testDeployApplication")
    public void testListSensors() {
        Set<SensorSummary> sensors = client().resource("/v1/applications/simple-app/entities/simple-ent/sensors")
                .get(new GenericType<Set<SensorSummary>>() { });
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
                .get(new GenericType<Set<EntityConfigSummary>>() { });
        assertTrue(config.size() > 0);
        System.out.println(("CONFIG: " + config));
    }

    @Test(dependsOnMethods = "testListConfig")
    public void testListConfig2() {
        Set<EntityConfigSummary> config = client().resource("/v1/applications/simple-app/entities/simple-ent/config")
                .get(new GenericType<Set<EntityConfigSummary>>() {});
        assertTrue(config.size() > 0);
        System.out.println(("CONFIG: " + config));
    }

    @Test(dependsOnMethods = "testDeployApplication")
    public void testListEffectors() {
        Set<EffectorSummary> effectors = client().resource("/v1/applications/simple-app/entities/simple-ent/effectors")
                .get(new GenericType<Set<EffectorSummary>>() {});

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
        ClientResponse response = client()
                .resource("/v1/applications/simple-app/entities/simple-ent/effectors/"+RestMockSimpleEntity.SAMPLE_EFFECTOR.getName())
                .type(MediaType.APPLICATION_JSON_TYPE)
                .post(ClientResponse.class, ImmutableMap.of("param1", "foo", "param2", 4));

        assertEquals(response.getStatus(), Response.Status.ACCEPTED.getStatusCode());

        String result = response.getEntity(String.class);
        assertEquals(result, "foo4");
    }

    @Test(dependsOnMethods = "testListSensors")
    public void testTriggerSampleEffectorWithFormData() throws InterruptedException, IOException {
        MultivaluedMap<String, String> data = new MultivaluedMapImpl();
        data.add("param1", "foo");
        data.add("param2", "4");
        ClientResponse response = client()
                .resource("/v1/applications/simple-app/entities/simple-ent/effectors/"+RestMockSimpleEntity.SAMPLE_EFFECTOR.getName())
                .type(MediaType.APPLICATION_FORM_URLENCODED_TYPE)
                .post(ClientResponse.class, data);

        assertEquals(response.getStatus(), Response.Status.ACCEPTED.getStatusCode());

        String result = response.getEntity(String.class);
        assertEquals(result, "foo4");
    }

    @Test(dependsOnMethods = "testTriggerSampleEffector")
    public void testBatchSensorValues() {
        WebResource resource = client().resource("/v1/applications/simple-app/entities/simple-ent/sensors/current-state");
        Map<String, Object> sensors = resource.get(new GenericType<Map<String, Object>>() {});
        assertTrue(sensors.size() > 0);
        assertEquals(sensors.get(RestMockSimpleEntity.SAMPLE_SENSOR.getName()), "foo4");
    }

    @Test(dependsOnMethods = "testBatchSensorValues")
    public void testReadEachSensor() {
    Set<SensorSummary> sensors = client().resource("/v1/applications/simple-app/entities/simple-ent/sensors")
            .get(new GenericType<Set<SensorSummary>>() {});

        Map<String, String> readings = Maps.newHashMap();
        for (SensorSummary sensor : sensors) {
            try {
                readings.put(sensor.getName(), client().resource(sensor.getLinks().get("self")).accept(MediaType.TEXT_PLAIN).get(String.class));
            } catch (UniformInterfaceException uie) {
                if (uie.getResponse().getStatus() == 204) { // no content
                    readings.put(sensor.getName(), null);
                } else {
                    Exceptions.propagate(uie);
                }
            }
        }

        assertEquals(readings.get(RestMockSimpleEntity.SAMPLE_SENSOR.getName()), "foo4");
    }

    @Test(dependsOnMethods = "testTriggerSampleEffector")
    public void testPolicyWhichCapitalizes() {
        String policiesEndpoint = "/v1/applications/simple-app/entities/simple-ent/policies";
        Set<PolicySummary> policies = client().resource(policiesEndpoint).get(new GenericType<Set<PolicySummary>>(){});
        assertEquals(policies.size(), 0);

        ClientResponse response = client().resource(policiesEndpoint)
                .queryParam("type", CapitalizePolicy.class.getCanonicalName())
                .type(MediaType.APPLICATION_JSON_TYPE)
                .post(ClientResponse.class, Maps.newHashMap());
        assertEquals(response.getStatus(), 200);
        PolicySummary policy = response.getEntity(PolicySummary.class);
        assertNotNull(policy.getId());
        String newPolicyId = policy.getId();
        log.info("POLICY CREATED: " + newPolicyId);
        policies = client().resource(policiesEndpoint).get(new GenericType<Set<PolicySummary>>() {});
        assertEquals(policies.size(), 1);

        Lifecycle status = client().resource(policiesEndpoint + "/" + newPolicyId).get(Lifecycle.class);
        log.info("POLICY STATUS: " + status);

        response = client().resource(policiesEndpoint+"/"+newPolicyId+"/start")
                .post(ClientResponse.class);
        assertEquals(response.getStatus(), 204);
        status = client().resource(policiesEndpoint + "/" + newPolicyId).get(Lifecycle.class);
        assertEquals(status, Lifecycle.RUNNING);

        response = client().resource(policiesEndpoint+"/"+newPolicyId+"/stop")
                .post(ClientResponse.class);
        assertEquals(response.getStatus(), 204);
        status = client().resource(policiesEndpoint + "/" + newPolicyId).get(Lifecycle.class);
        assertEquals(status, Lifecycle.STOPPED);

        response = client().resource(policiesEndpoint+"/"+newPolicyId+"/destroy")
                .post(ClientResponse.class);
        assertEquals(response.getStatus(), 204);

        response = client().resource(policiesEndpoint+"/"+newPolicyId).get(ClientResponse.class);
        log.info("POLICY STATUS RESPONSE AFTER DESTROY: " + response.getStatus());
        assertEquals(response.getStatus(), 404);

        policies = client().resource(policiesEndpoint).get(new GenericType<Set<PolicySummary>>() {});
        assertEquals(0, policies.size());
    }

    @SuppressWarnings({ "rawtypes" })
    @Test(dependsOnMethods = "testDeployApplication")
    public void testLocatedLocation() {
        log.info("starting testLocatedLocations");
        testListApplications();

        LocationInternal l = (LocationInternal) getManagementContext().getApplications().iterator().next().getLocations().iterator().next();
        if (l.config().getLocalRaw(LocationConfigKeys.LATITUDE).isAbsent()) {
            log.info("Supplying fake locations for localhost because could not be autodetected");
            ((AbstractLocation) l).setHostGeoInfo(new HostGeoInfo("localhost", "localhost", 50, 0));
        }
        Map result = client().resource("/v1/locations/usage/LocatedLocations")
                .get(Map.class);
        log.info("LOCATIONS: " + result);
        Assert.assertEquals(result.size(), 1);
        Map details = (Map) result.values().iterator().next();
        assertEquals(details.get("leafEntityCount"), 2);
    }

    @Test(dependsOnMethods = {"testListEffectors", "testFetchApplicationsAndEntity", "testTriggerSampleEffector", "testListApplications","testReadEachSensor","testPolicyWhichCapitalizes","testLocatedLocation"})
    public void testDeleteApplication() throws TimeoutException, InterruptedException {
        waitForPageFoundResponse("/v1/applications/simple-app", ApplicationSummary.class);
        Collection<Application> apps = getManagementContext().getApplications();
        log.info("Deleting simple-app from " + apps);
        int size = apps.size();

        ClientResponse response = client().resource("/v1/applications/simple-app")
                .delete(ClientResponse.class);

        assertEquals(response.getStatus(), Response.Status.ACCEPTED.getStatusCode());
        TaskSummary task = response.getEntity(TaskSummary.class);
        assertTrue(task.getDescription().toLowerCase().contains("destroy"), task.getDescription());
        assertTrue(task.getDescription().toLowerCase().contains("simple-app"), task.getDescription());

        waitForPageNotFoundResponse("/v1/applications/simple-app", ApplicationSummary.class);

        log.info("App appears gone, apps are: " + getManagementContext().getApplications());
        // more logging above, for failure in the check below

        Asserts.eventually(
                EntityFunctions.applications(getManagementContext()),
                Predicates.compose(Predicates.equalTo(size-1), CollectionFunctionals.sizeFunction()) );
    }

    @Test
    public void testDisabledApplicationCatalog() throws TimeoutException, InterruptedException {
        String itemSymbolicName = "my.catalog.item.id.for.disabling";
        String itemVersion = "1.0";
        String serviceType = "org.apache.brooklyn.entity.stock.BasicApplication";
        
        // Deploy the catalog item
        addTestCatalogItem(itemSymbolicName, "template", itemVersion, serviceType);
        List<CatalogEntitySummary> itemSummaries = client().resource("/v1/catalog/applications")
                .queryParam("fragment", itemSymbolicName).queryParam("allVersions", "true").get(new GenericType<List<CatalogEntitySummary>>() {});
        CatalogItemSummary itemSummary = Iterables.getOnlyElement(itemSummaries);
        String itemVersionedId = String.format("%s:%s", itemSummary.getSymbolicName(), itemSummary.getVersion());
        assertEquals(itemSummary.getId(), itemVersionedId);

        try {
            // Create an app before disabling: this should work
            String yaml = "{ name: my-app, location: localhost, services: [ { type: \""+itemVersionedId+"\" } ] }";
            ClientResponse response = client().resource("/v1/applications")
                    .entity(yaml, "application/x-yaml")
                    .post(ClientResponse.class);
            HttpAsserts.assertHealthyStatusCode(response.getStatus());
            waitForPageFoundResponse("/v1/applications/my-app", ApplicationSummary.class);
    
            // Deprecate
            deprecateCatalogItem(itemSymbolicName, itemVersion, true);

            // Create an app when deprecated: this should work
            String yaml2 = "{ name: my-app2, location: localhost, services: [ { type: \""+itemVersionedId+"\" } ] }";
            ClientResponse response2 = client().resource("/v1/applications")
                    .entity(yaml2, "application/x-yaml")
                    .post(ClientResponse.class);
            HttpAsserts.assertHealthyStatusCode(response2.getStatus());
            waitForPageFoundResponse("/v1/applications/my-app2", ApplicationSummary.class);
    
            // Disable
            disableCatalogItem(itemSymbolicName, itemVersion, true);

            // Now try creating an app; this should fail because app is disabled
            String yaml3 = "{ name: my-app3, location: localhost, services: [ { type: \""+itemVersionedId+"\" } ] }";
            ClientResponse response3 = client().resource("/v1/applications")
                    .entity(yaml3, "application/x-yaml")
                    .post(ClientResponse.class);
            HttpAsserts.assertClientErrorStatusCode(response3.getStatus());
            assertTrue(response3.getEntity(String.class).contains("cannot be matched"));
            waitForPageNotFoundResponse("/v1/applications/my-app3", ApplicationSummary.class);
            
        } finally {
            client().resource("/v1/applications/my-app")
                    .delete(ClientResponse.class);

            client().resource("/v1/applications/my-app2")
                    .delete(ClientResponse.class);

            client().resource("/v1/applications/my-app3")
                    .delete(ClientResponse.class);

            client().resource("/v1/catalog/entities/"+itemVersionedId+"/"+itemVersion)
                    .delete(ClientResponse.class);
        }
    }

    private void deprecateCatalogItem(String symbolicName, String version, boolean deprecated) {
        String id = String.format("%s:%s", symbolicName, version);
        ClientResponse response = client().resource(String.format("/v1/catalog/entities/%s/deprecated", id))
                    .header(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON)
                    .post(ClientResponse.class, deprecated);
        assertEquals(response.getStatus(), Response.Status.NO_CONTENT.getStatusCode());
    }
    
    private void disableCatalogItem(String symbolicName, String version, boolean disabled) {
        String id = String.format("%s:%s", symbolicName, version);
        ClientResponse response = client().resource(String.format("/v1/catalog/entities/%s/disabled", id))
                .header(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON)
                .post(ClientResponse.class, disabled);
        assertEquals(response.getStatus(), Response.Status.NO_CONTENT.getStatusCode());
    }

    private void addTestCatalogItem(String catalogItemId, String itemType, String version, String service) {
        String yaml =
                "brooklyn.catalog:\n"+
                "  id: " + catalogItemId + "\n"+
                "  name: My Catalog App\n"+
                (itemType!=null ? "  item_type: "+itemType+"\n" : "")+
                "  description: My description\n"+
                "  icon_url: classpath:///redis-logo.png\n"+
                "  version: " + version + "\n"+
                "\n"+
                "services:\n"+
                "- type: " + service + "\n";

        client().resource("/v1/catalog").post(yaml);
    }
}
