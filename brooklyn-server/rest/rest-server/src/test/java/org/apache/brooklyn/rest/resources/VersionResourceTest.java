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

import javax.ws.rs.core.Response;

import org.testng.annotations.Test;

import org.apache.brooklyn.rest.testing.BrooklynRestResourceTest;

import com.sun.jersey.api.client.ClientResponse;

public class VersionResourceTest extends BrooklynRestResourceTest {

    @Test
    public void testGetVersion() {
        ClientResponse response = client().resource("/v1/version")
                .get(ClientResponse.class);

        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
        String version = response.getEntity(String.class);
// TODO johnmccabe - 19/12/2015 :: temporarily disabled while the repo split work is ongoing,
// must be restored when switching back to a valid brooklyn version
//        assertTrue(version.matches("^\\d+\\.\\d+\\.\\d+.*"));
        assertTrue(true);
    }

    @SuppressWarnings("deprecation")
    @Override
    protected void addBrooklynResources() {
        addResource(new VersionResource());
    }
}
