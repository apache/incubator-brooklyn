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
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.fail;

import java.util.Map;
import java.util.Set;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import org.apache.brooklyn.rest.domain.ApplicationSpec;
import org.apache.brooklyn.rest.domain.EntitySpec;
import org.apache.brooklyn.rest.domain.PolicyConfigSummary;
import org.apache.brooklyn.rest.domain.PolicySummary;
import org.apache.brooklyn.rest.testing.BrooklynRestResourceTest;
import org.apache.brooklyn.rest.testing.mocks.RestMockSimpleEntity;
import org.apache.brooklyn.rest.testing.mocks.RestMockSimplePolicy;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.GenericType;

@Test(singleThreaded = true)
public class PolicyResourceTest extends BrooklynRestResourceTest {

    @SuppressWarnings("unused")
    private static final Logger log = LoggerFactory.getLogger(PolicyResourceTest.class);

    private static final String ENDPOINT = "/v1/applications/simple-app/entities/simple-ent/policies/";

    private final ApplicationSpec simpleSpec = ApplicationSpec.builder().name("simple-app").entities(
            ImmutableSet.of(new EntitySpec("simple-ent", RestMockSimpleEntity.class.getName()))).locations(
            ImmutableSet.of("localhost")).build();

    private String policyId;

    @BeforeClass(alwaysRun = true)
    @Override
    public void setUp() throws Exception {
        super.setUp();

        ClientResponse aResponse = clientDeploy(simpleSpec);
        waitForApplicationToBeRunning(aResponse.getLocation());

        ClientResponse pResponse = client().resource(ENDPOINT)
                .queryParam("type", RestMockSimplePolicy.class.getCanonicalName())
                .type(MediaType.APPLICATION_JSON_TYPE)
                .post(ClientResponse.class, Maps.newHashMap());

        PolicySummary response = pResponse.getEntity(PolicySummary.class);
        assertNotNull(response.getId());
        policyId = response.getId();

    }

    @Test
    public void testListConfig() throws Exception {
        Set<PolicyConfigSummary> config = client().resource(ENDPOINT + policyId + "/config")
                .get(new GenericType<Set<PolicyConfigSummary>>() {});
        
        Set<String> configNames = Sets.newLinkedHashSet();
        for (PolicyConfigSummary conf : config) {
            configNames.add(conf.getName());
        }

        assertEquals(configNames, ImmutableSet.of(
                RestMockSimplePolicy.SAMPLE_CONFIG.getName(),
                RestMockSimplePolicy.INTEGER_CONFIG.getName()));
    }

    @Test
    public void testGetNonExistantConfigReturns404() throws Exception {
        String invalidConfigName = "doesnotexist";
        try {
            PolicyConfigSummary summary = client().resource(ENDPOINT + policyId + "/config/" + invalidConfigName)
                    .get(PolicyConfigSummary.class);
            fail("Should have thrown 404, but got "+summary);
        } catch (Exception e) {
            if (!e.toString().contains("404")) throw e;
        }
    }

    @Test
    public void testGetDefaultValue() throws Exception {
        String configName = RestMockSimplePolicy.SAMPLE_CONFIG.getName();
        String expectedVal = RestMockSimplePolicy.SAMPLE_CONFIG.getDefaultValue();
        
        String configVal = client().resource(ENDPOINT + policyId + "/config/" + configName)
                .get(String.class);
        assertEquals(configVal, expectedVal);
    }
    
    @Test(dependsOnMethods = "testGetDefaultValue")
    public void testReconfigureConfig() throws Exception {
        String configName = RestMockSimplePolicy.SAMPLE_CONFIG.getName();
        
        ClientResponse response = client().resource(ENDPOINT + policyId + "/config/" + configName + "/set")
                .queryParam("value", "newval")
                .post(ClientResponse.class);

        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
    }
    
    @Test(dependsOnMethods = "testReconfigureConfig")
    public void testGetConfigValue() throws Exception {
        String configName = RestMockSimplePolicy.SAMPLE_CONFIG.getName();
        String expectedVal = "newval";
        
        Map<String, Object> allState = client().resource(ENDPOINT + policyId + "/config/current-state")
                .get(new GenericType<Map<String, Object>>() {});
        assertEquals(allState, ImmutableMap.of(configName, expectedVal));
        
        String configVal = client().resource(ENDPOINT + policyId + "/config/" + configName)
                .get(String.class);
        assertEquals(configVal, expectedVal);
    }
}
