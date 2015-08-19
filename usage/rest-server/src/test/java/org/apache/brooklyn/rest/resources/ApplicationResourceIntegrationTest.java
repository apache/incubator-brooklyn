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

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.net.URI;
import java.util.Set;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;
import org.apache.brooklyn.entity.lifecycle.Lifecycle;
import org.apache.brooklyn.rest.domain.ApplicationSpec;
import org.apache.brooklyn.rest.domain.ApplicationSummary;
import org.apache.brooklyn.rest.domain.EntitySpec;
import org.apache.brooklyn.rest.domain.EntitySummary;
import org.apache.brooklyn.rest.domain.SensorSummary;
import org.apache.brooklyn.rest.testing.BrooklynRestResourceTest;
import org.apache.brooklyn.test.Asserts;
import org.apache.brooklyn.util.collections.MutableMap;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.GenericType;

@Test(singleThreaded = true)
public class ApplicationResourceIntegrationTest extends BrooklynRestResourceTest {

    @SuppressWarnings("unused")
    private static final Logger log = LoggerFactory.getLogger(ApplicationResourceIntegrationTest.class);

    private final ApplicationSpec redisSpec = ApplicationSpec.builder().name("redis-app")
            .entities(ImmutableSet.of(new EntitySpec("redis-ent", "org.apache.brooklyn.entity.nosql.redis.RedisStore")))
            .locations(ImmutableSet.of("localhost"))
            .build();

    @Test(groups="Integration")
    public void testDeployRedisApplication() throws Exception {
        ClientResponse response = clientDeploy(redisSpec);

        assertEquals(response.getStatus(), 201);
        assertEquals(getManagementContext().getApplications().size(), 1);
        assertTrue(response.getLocation().getPath().startsWith("/v1/applications/"), "path="+response.getLocation().getPath()); // path uses id, rather than app name

        waitForApplicationToBeRunning(response.getLocation());
    }

    @Test(groups="Integration", dependsOnMethods = "testDeployRedisApplication")
    public void testListEntities() {
        Set<EntitySummary> entities = client().resource("/v1/applications/redis-app/entities")
                .get(new GenericType<Set<EntitySummary>>() {});

        for (EntitySummary entity : entities) {
            client().resource(entity.getLinks().get("self")).get(ClientResponse.class);
            // TODO assertions on the above call?

            Set<EntitySummary> children = client().resource(entity.getLinks().get("children"))
                    .get(new GenericType<Set<EntitySummary>>() {});
            assertEquals(children.size(), 0);
        }
    }

    @Test(groups="Integration", dependsOnMethods = "testDeployRedisApplication")
    public void testListSensorsRedis() {
        Set<SensorSummary> sensors = client().resource("/v1/applications/redis-app/entities/redis-ent/sensors")
                .get(new GenericType<Set<SensorSummary>>() {});
        assertTrue(sensors.size() > 0);
        SensorSummary uptime = Iterables.find(sensors, new Predicate<SensorSummary>() {
            @Override
            public boolean apply(SensorSummary sensorSummary) {
                return sensorSummary.getName().equals("redis.uptime");
            }
        });
        assertEquals(uptime.getType(), "java.lang.Integer");
    }

    @Test(groups="Integration", dependsOnMethods = { "testListSensorsRedis", "testListEntities" })
    public void testTriggerRedisStopEffector() throws Exception {
        ClientResponse response = client().resource("/v1/applications/redis-app/entities/redis-ent/effectors/stop")
                .type(MediaType.APPLICATION_JSON_TYPE)
                .post(ClientResponse.class, ImmutableMap.of());
        assertEquals(response.getStatus(), Response.Status.ACCEPTED.getStatusCode());

        final URI stateSensor = URI.create("/v1/applications/redis-app/entities/redis-ent/sensors/service.state");
        final String expectedStatus = Lifecycle.STOPPED.toString();
        Asserts.succeedsEventually(MutableMap.of("timeout", 60 * 1000), new Runnable() {
            public void run() {
                // Accept with and without quotes; if don't specify "Accepts" header, then
                // might get back json or plain text (depending on compiler / java runtime 
                // used for SensorApi!)
                String val = client().resource(stateSensor).get(String.class);
                assertTrue(expectedStatus.equalsIgnoreCase(val) || ("\""+expectedStatus+"\"").equalsIgnoreCase(val), "state="+val);
            }
        });
    }

    @Test(groups="Integration", dependsOnMethods = "testTriggerRedisStopEffector" )
    public void testDeleteRedisApplication() throws Exception {
        int size = getManagementContext().getApplications().size();
        ClientResponse response = client().resource("/v1/applications/redis-app")
                .delete(ClientResponse.class);

        waitForPageNotFoundResponse("/v1/applications/redis-app", ApplicationSummary.class);

        assertEquals(response.getStatus(), Response.Status.ACCEPTED.getStatusCode());
        assertEquals(getManagementContext().getApplications().size(), size-1);
    }

}
