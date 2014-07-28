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
import static org.testng.Assert.assertTrue;

import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import brooklyn.rest.domain.AccessSummary;
import brooklyn.rest.testing.BrooklynRestResourceTest;

import com.sun.jersey.api.client.ClientResponse;

@Test(singleThreaded = true)
public class AccessResourceTest extends BrooklynRestResourceTest {

    @SuppressWarnings("unused")
    private static final Logger log = LoggerFactory.getLogger(AccessResourceTest.class);

    @Test
    public void testGetAndSetAccessControl() throws Exception {
        // Default is everything allowed
        AccessSummary summary = client().resource("/v1/access").get(AccessSummary.class);
        assertTrue(summary.isLocationProvisioningAllowed());

        // Forbid location provisioning
        ClientResponse response = client().resource(
                "/v1/access/locationProvisioningAllowed")
                .queryParam("allowed", "false")
                .post(ClientResponse.class);
        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());

        AccessSummary summary2 = client().resource("/v1/access").get(AccessSummary.class);
        assertFalse(summary2.isLocationProvisioningAllowed());
        
        // Allow location provisioning
        ClientResponse response2 = client().resource(
                "/v1/access/locationProvisioningAllowed")
                .queryParam("allowed", "true")
                .post(ClientResponse.class);
        assertEquals(response2.getStatus(), Response.Status.OK.getStatusCode());

        AccessSummary summary3 = client().resource("/v1/access").get(AccessSummary.class);
        assertTrue(summary3.isLocationProvisioningAllowed());
    }
}
