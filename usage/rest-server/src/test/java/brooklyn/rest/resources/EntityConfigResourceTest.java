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
package brooklyn.rest.resources;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.net.URI;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.basic.EntityPredicates;
import brooklyn.rest.domain.ApplicationSpec;
import brooklyn.rest.domain.EntityConfigSummary;
import brooklyn.rest.domain.EntitySpec;
import brooklyn.rest.testing.BrooklynRestResourceTest;
import brooklyn.rest.testing.mocks.RestMockSimpleEntity;
import brooklyn.util.collections.MutableMap;

import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.GenericType;

@Test(singleThreaded = true)
public class EntityConfigResourceTest extends BrooklynRestResourceTest {
    
    private final static Logger log = LoggerFactory.getLogger(EntityConfigResourceTest.class);
    private URI applicationUri;
    private EntityInternal entity;

    @BeforeClass(alwaysRun = true)
    @Override
    public void setUp() throws Exception {
        super.setUp(); // We require that the superclass setup is done first, as we will be calling out to Jersey

        // Deploy an application that we'll use to read the configuration of
        final ApplicationSpec simpleSpec = ApplicationSpec.builder().name("simple-app").
                  entities(ImmutableSet.of(new EntitySpec("simple-ent", RestMockSimpleEntity.class.getName(), ImmutableMap.of("install.version", "1.0.0")))).
                  locations(ImmutableSet.of("localhost")).
                  build();
        
        ClientResponse response = clientDeploy(simpleSpec);
        int status = response.getStatus();
        assertTrue(status >= 200 && status <= 299, "expected HTTP Response of 2xx but got " + status);
        applicationUri = response.getLocation();
        log.debug("Built app: application");
        waitForApplicationToBeRunning(applicationUri);
        
        entity = (EntityInternal) Iterables.find(getManagementContext().getEntityManager().getEntities(), EntityPredicates.displayNameEqualTo("simple-ent"));
    }

    @Test
    public void testList() throws Exception {
        List<EntityConfigSummary> entityConfigSummaries = client().resource(
                URI.create("/v1/applications/simple-app/entities/simple-ent/config"))
                .get(new GenericType<List<EntityConfigSummary>>() {
                });
        
        // Default entities have over a dozen config entries, but it's unnecessary to test them all; just pick one
        // representative config key
        Optional<EntityConfigSummary> configKeyOptional = Iterables.tryFind(entityConfigSummaries, new Predicate<EntityConfigSummary>() {
            @Override
            public boolean apply(@Nullable EntityConfigSummary input) {
                return input != null && "install.version".equals(input.getName());
            }
        });
        assertTrue(configKeyOptional.isPresent());
        
        assertEquals(configKeyOptional.get().getType(), "java.lang.String");
        assertEquals(configKeyOptional.get().getDescription(), "Suggested version");
        assertFalse(configKeyOptional.get().isReconfigurable());
        assertNull(configKeyOptional.get().getDefaultValue());
        assertNull(configKeyOptional.get().getLabel());
        assertNull(configKeyOptional.get().getPriority());
    }

    @Test
    public void testBatchConfigRead() throws Exception {
        Map<String, Object> currentState = client().resource(
                URI.create("/v1/applications/simple-app/entities/simple-ent/config/current-state"))
                .get(new GenericType<Map<String, Object>>() {
                });
        assertTrue(currentState.containsKey("install.version"));
        assertEquals(currentState.get("install.version"), "1.0.0");
    }

    @Test
    public void testGetJson() throws Exception {
        String configValue = client().resource(
                URI.create("/v1/applications/simple-app/entities/simple-ent/config/install.version"))
                .accept(MediaType.APPLICATION_JSON_TYPE)
                .get(String.class);
        assertEquals(configValue, "\"1.0.0\"");
    }

    @Test
    public void testGetPlain() throws Exception {
        String configValue = client().resource(
                URI.create("/v1/applications/simple-app/entities/simple-ent/config/install.version"))
                .accept(MediaType.TEXT_PLAIN_TYPE)
                .get(String.class);
        assertEquals(configValue, "1.0.0");
    }

    @Test
    public void testSet() throws Exception {
        try {
            String uri = "/v1/applications/simple-app/entities/simple-ent/config/"+
                RestMockSimpleEntity.SAMPLE_CONFIG.getName();
            ClientResponse response = client().resource(uri)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .post(ClientResponse.class, "\"hello world\"");
            assertEquals(response.getStatus(), Response.Status.NO_CONTENT.getStatusCode());

            assertEquals(entity.getConfig(RestMockSimpleEntity.SAMPLE_CONFIG), "hello world");
            
            String value = client().resource(uri).accept(MediaType.APPLICATION_JSON_TYPE).get(String.class);
            assertEquals(value, "\"hello world\"");

        } finally { entity.setConfig(RestMockSimpleEntity.SAMPLE_CONFIG, RestMockSimpleEntity.SAMPLE_CONFIG.getDefaultValue()); }
    }

    @Test
    public void testSetFromMap() throws Exception {
        try {
            String uri = "/v1/applications/simple-app/entities/simple-ent/config";
            ClientResponse response = client().resource(uri)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .post(ClientResponse.class, MutableMap.of(
                    RestMockSimpleEntity.SAMPLE_CONFIG.getName(), "hello world"));
            assertEquals(response.getStatus(), Response.Status.NO_CONTENT.getStatusCode());

            assertEquals(entity.getConfig(RestMockSimpleEntity.SAMPLE_CONFIG), "hello world");
            
            String value = client().resource(uri+"/"+RestMockSimpleEntity.SAMPLE_CONFIG.getName()).accept(MediaType.APPLICATION_JSON_TYPE).get(String.class);
            assertEquals(value, "\"hello world\"");

        } finally { entity.setConfig(RestMockSimpleEntity.SAMPLE_CONFIG, RestMockSimpleEntity.SAMPLE_CONFIG.getDefaultValue()); }
    }

}
