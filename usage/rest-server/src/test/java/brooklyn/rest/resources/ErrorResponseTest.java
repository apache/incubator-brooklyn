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
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response.Status;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import brooklyn.rest.domain.ApiError;
import brooklyn.rest.domain.ApplicationSpec;
import brooklyn.rest.domain.EntitySpec;
import brooklyn.rest.domain.PolicySummary;
import brooklyn.rest.testing.BrooklynRestResourceTest;
import brooklyn.rest.testing.mocks.RestMockSimpleEntity;
import brooklyn.rest.testing.mocks.RestMockSimplePolicy;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.sun.jersey.api.client.ClientResponse;

public class ErrorResponseTest extends BrooklynRestResourceTest {

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

        String policiesEndpoint = "/v1/applications/simple-app/entities/simple-ent/policies";

        ClientResponse pResponse = client().resource(policiesEndpoint)
                .queryParam("type", RestMockSimplePolicy.class.getCanonicalName())
                .post(ClientResponse.class, Maps.newHashMap());
        PolicySummary response = pResponse.getEntity(PolicySummary.class);
        assertNotNull(response.getId());
        policyId = response.getId();
    }

    @Test
    public void testResponseToBadRequest() {
        String resource = "/v1/applications/simple-app/entities/simple-ent/policies/"+policyId+"/config/"
                + RestMockSimplePolicy.INTEGER_CONFIG.getName() + "/set";

        ClientResponse response = client().resource(resource)
                .queryParam("value", "notanumber")
                .post(ClientResponse.class);

        assertEquals(response.getStatus(), Status.BAD_REQUEST.getStatusCode());
        assertEquals(response.getHeaders().getFirst("Content-Type"), MediaType.APPLICATION_JSON);

        ApiError error = response.getEntity(ApiError.class);
        assertTrue(error.getMessage().toLowerCase().contains("cannot coerce"));
    }

    @Test
    public void testResponseToWrongMethod() {
        String resource = "/v1/applications/simple-app/entities/simple-ent/policies/"+policyId+"/config/"
                + RestMockSimplePolicy.INTEGER_CONFIG.getName() + "/set";

        // Should be POST, not GET
        ClientResponse response = client().resource(resource)
                .queryParam("value", "4")
                .get(ClientResponse.class);

        assertEquals(response.getStatus(), 405);
        // Can we assert anything about the content type?
    }
}
