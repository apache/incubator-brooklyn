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
package org.apache.brooklyn.rest.client;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.util.Collection;

import javax.ws.rs.core.Response;

import org.eclipse.jetty.server.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import brooklyn.entity.Application;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.entity.basic.StartableApplication;
import brooklyn.location.basic.BasicLocationRegistry;
import brooklyn.management.internal.LocalManagementContext;

import org.apache.brooklyn.management.ManagementContext;
import org.apache.brooklyn.rest.BrooklynRestApiLauncher;
import org.apache.brooklyn.rest.BrooklynRestApiLauncherTest;
import org.apache.brooklyn.rest.domain.ApplicationSpec;
import org.apache.brooklyn.rest.domain.ApplicationSummary;
import org.apache.brooklyn.rest.domain.EntitySpec;
import org.apache.brooklyn.rest.domain.EntitySummary;
import org.apache.brooklyn.rest.domain.SensorSummary;

import brooklyn.test.Asserts;
import brooklyn.util.time.Duration;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

@Test(singleThreaded = true)
public class ApplicationResourceIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(ApplicationResourceIntegrationTest.class);

    private static final Duration LONG_WAIT = Duration.minutes(10);
    
    private final String redisSpec = "{\"name\": \"redis-app\", \"type\": \"org.apache.brooklyn.entity.nosql.redis.RedisStore\", \"locations\": [ \"localhost\"]}";
    
    private final ApplicationSpec legacyRedisSpec = ApplicationSpec.builder().name("redis-legacy-app")
            .entities(ImmutableSet.of(new EntitySpec("redis-ent", "org.apache.brooklyn.entity.nosql.redis.RedisStore")))
            .locations(ImmutableSet.of("localhost"))
            .build();

    private ManagementContext manager;

    protected synchronized ManagementContext getManagementContext() throws Exception {
        if (manager == null) {
            manager = new LocalManagementContext();
            BrooklynRestApiLauncherTest.forceUseOfDefaultCatalogWithJavaClassPath(manager);
            BasicLocationRegistry.setupLocationRegistryForTesting(manager);
            BrooklynRestApiLauncherTest.enableAnyoneLogin(manager);
        }
        return manager;
    }

    BrooklynApi api;

    @BeforeClass(groups = "Integration")
    public void setUp() throws Exception {
        Server server = BrooklynRestApiLauncher.launcher()
                .managementContext(getManagementContext())
                .start();

        api = new BrooklynApi("http://localhost:" + server.getConnectors()[0].getPort() + "/");
    }

    @AfterClass(alwaysRun = true)
    public void tearDown() throws Exception {
        for (Application app : getManagementContext().getApplications()) {
            try {
                ((StartableApplication) app).stop();
            } catch (Exception e) {
                log.warn("Error stopping app " + app + " during test teardown: " + e);
            }
        }
    }

    @Test(groups = "Integration")
    public void testDeployRedisApplication() throws Exception {
        Response response = api.getApplicationApi().createPoly(redisSpec.getBytes());
        assertEquals(response.getStatus(), 201);
        assertEquals(getManagementContext().getApplications().size(), 1);
        final String entityId = getManagementContext().getApplications().iterator().next().getChildren().iterator().next().getId();
        assertServiceStateEventually("redis-app", entityId, Lifecycle.RUNNING, LONG_WAIT);
    }
    
    @Test(groups = "Integration", dependsOnMethods = "testDeployRedisApplication")
    public void testDeployLegacyRedisApplication() throws Exception {
        @SuppressWarnings("deprecation")
        Response response = api.getApplicationApi().create(legacyRedisSpec);
        assertEquals(response.getStatus(), 201);
        assertEquals(getManagementContext().getApplications().size(), 2);
        assertServiceStateEventually("redis-legacy-app", "redis-ent", Lifecycle.RUNNING, LONG_WAIT);
        
        // Tear the app down so it doesn't interfere with other tests 
        Response deleteResponse = api.getApplicationApi().delete("redis-legacy-app");
        assertEquals(deleteResponse.getStatus(), 202);
        assertEquals(getManagementContext().getApplications().size(), 1);
    }

    @Test(groups = "Integration", dependsOnMethods = "testDeployRedisApplication")
    public void testListEntities() {
        Collection<EntitySummary> entities = api.getEntityApi().list("redis-app");
        Assert.assertFalse(entities.isEmpty());
    }

    @Test(groups = "Integration", dependsOnMethods = "testDeployRedisApplication")
    public void testListSensorsRedis() throws Exception {
        String entityId = getManagementContext().getApplications().iterator().next().getChildren().iterator().next().getId();
        Collection<SensorSummary> sensors = api.getSensorApi().list("redis-app", entityId);
        assertTrue(sensors.size() > 0);
        SensorSummary uptime = Iterables.find(sensors, new Predicate<SensorSummary>() {
            @Override
            public boolean apply(SensorSummary sensorSummary) {
                return sensorSummary.getName().equals("redis.uptime");
            }
        });
        assertEquals(uptime.getType(), "java.lang.Integer");
    }

    @Test(groups = "Integration", dependsOnMethods = {"testListSensorsRedis", "testListEntities"})
    public void testTriggerRedisStopEffector() throws Exception {
        final String entityId = getManagementContext().getApplications().iterator().next().getChildren().iterator().next().getId();
        Response response = api.getEffectorApi().invoke("redis-app", entityId, "stop", "5000", ImmutableMap.<String, Object>of());

        assertEquals(response.getStatus(), Response.Status.ACCEPTED.getStatusCode());
        assertServiceStateEventually("redis-app", entityId, Lifecycle.STOPPED, LONG_WAIT);
    }

    @Test(groups = "Integration", dependsOnMethods = "testTriggerRedisStopEffector")
    public void testDeleteRedisApplication() throws Exception {
        int size = getManagementContext().getApplications().size();
        Response response = api.getApplicationApi().delete("redis-app");
        Assert.assertNotNull(response);
        try {
            Asserts.succeedsEventually(ImmutableMap.of("timeout", Duration.minutes(1)), new Runnable() {
                public void run() {
                    try {
                        ApplicationSummary summary = api.getApplicationApi().get("redis-app");
                        fail("Redis app failed to disappear: summary="+summary);
                    } catch (Exception failure) {
                        // expected -- it will be a ClientResponseFailure but that class is deprecated so catching all
                        // and asserting contains the word 404
                        Assert.assertTrue(failure.toString().indexOf("404") >= 0, "wrong failure, got: "+failure);
                    }
                }});
        } catch (Exception failure) {
            // expected -- as above
            Assert.assertTrue(failure.toString().indexOf("404") >= 0, "wrong failure, got: "+failure);
        }

        assertEquals(getManagementContext().getApplications().size(), size - 1);
    }

    private void assertServiceStateEventually(final String app, final String entity, final Lifecycle state, Duration timeout) {
        Asserts.succeedsEventually(ImmutableMap.of("timeout", timeout), new Runnable() {
            public void run() {
                Object status = api.getSensorApi().get(app, entity, "service.state", false);
                assertTrue(state.toString().equalsIgnoreCase(status.toString()), "status="+status);
            }});
    }
}
