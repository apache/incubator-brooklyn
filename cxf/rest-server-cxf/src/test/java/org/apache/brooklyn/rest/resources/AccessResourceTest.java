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
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import org.apache.brooklyn.rest.domain.AccessSummary;
import org.apache.brooklyn.rest.testing.BrooklynRestResourceTest;

@Test(singleThreaded = true,
        // by using a different suite name we disallow interleaving other tests between the methods of this test class, which wrecks the test fixtures
        suiteName = "AccessResourceTest")
public class AccessResourceTest extends BrooklynRestResourceTest {

    @SuppressWarnings("unused")
    private static final Logger log = LoggerFactory.getLogger(AccessResourceTest.class);

    @Test
    public void testGetAndSetAccessControl() throws Exception {
        // Default is everything allowed
        AccessSummary summary = client().path("/access").get(AccessSummary.class);
        assertTrue(summary.isLocationProvisioningAllowed());

        // Forbid location provisioning
        Response response = client().path("/access/locationProvisioningAllowed")
                .query("allowed", "false")
                .post(null);
        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());

        AccessSummary summary2 = client().path("/access").get(AccessSummary.class);
        assertFalse(summary2.isLocationProvisioningAllowed());

        // Allow location provisioning
        Response response2 = client().path("/access/locationProvisioningAllowed")
                .query("allowed", "true")
                .post(null);
        assertEquals(response2.getStatus(), Response.Status.OK.getStatusCode());

        AccessSummary summary3 = client().path("/access").get(AccessSummary.class);
        assertTrue(summary3.isLocationProvisioningAllowed());
    }

}
