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
package org.apache.brooklyn.rest;

import static org.testng.Assert.assertEquals;

import javax.ws.rs.core.MediaType;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.management.internal.LocalManagementContext;
import brooklyn.management.internal.ManagementContextInternal;

import org.apache.brooklyn.management.ha.HighAvailabilityManager;
import org.apache.brooklyn.management.ha.HighAvailabilityMode;
import org.apache.brooklyn.management.ha.ManagementNodeState;
import org.apache.brooklyn.rest.filter.HaHotCheckResourceFilter;
import org.apache.brooklyn.rest.filter.HaMasterCheckFilter;
import org.apache.brooklyn.rest.testing.BrooklynRestResourceTest;
import org.apache.brooklyn.rest.util.HaHotStateCheckClassResource;
import org.apache.brooklyn.rest.util.HaHotStateCheckResource;

import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource.Builder;
import com.sun.jersey.api.core.ResourceConfig;

public class HaHotCheckTest extends BrooklynRestResourceTest {

    // setup and teardown before/after each method
    
    @BeforeMethod(alwaysRun = true)
    public void setUp() throws Exception { super.setUp(); }

    @AfterMethod(alwaysRun = true)
    public void tearDown() throws Exception { super.tearDown(); }
    
    @Override
    protected void addBrooklynResources() {
        config.getProperties().put(ResourceConfig.PROPERTY_RESOURCE_FILTER_FACTORIES, 
            new HaHotCheckResourceFilter(getManagementContext()));
        addResource(new HaHotStateCheckResource());
        addResource(new HaHotStateCheckClassResource());
        
        ((LocalManagementContext)getManagementContext()).noteStartupComplete();
    }

    @Test
    public void testHaCheck() {
        HighAvailabilityManager ha = getManagementContext().getHighAvailabilityManager();
        assertEquals(ha.getNodeState(), ManagementNodeState.MASTER);
        testResourceFetch("/ha/method/ok", 200);
        testResourceFetch("/ha/method/fail", 200);
        testResourceFetch("/ha/class/fail", 200);

        getManagementContext().getHighAvailabilityManager().changeMode(HighAvailabilityMode.STANDBY);
        assertEquals(ha.getNodeState(), ManagementNodeState.STANDBY);

        testResourceFetch("/ha/method/ok", 200);
        testResourceFetch("/ha/method/fail", 403);
        testResourceFetch("/ha/class/fail", 403);

        ((ManagementContextInternal)getManagementContext()).terminate();
        assertEquals(ha.getNodeState(), ManagementNodeState.TERMINATED);

        testResourceFetch("/ha/method/ok", 200);
        testResourceFetch("/ha/method/fail", 403);
        testResourceFetch("/ha/class/fail", 403);
    }

    @Test
    public void testHaCheckForce() {
        HighAvailabilityManager ha = getManagementContext().getHighAvailabilityManager();
        assertEquals(ha.getNodeState(), ManagementNodeState.MASTER);
        testResourceForcedFetch("/ha/method/ok", 200);
        testResourceForcedFetch("/ha/method/fail", 200);
        testResourceForcedFetch("/ha/class/fail", 200);

        getManagementContext().getHighAvailabilityManager().changeMode(HighAvailabilityMode.STANDBY);
        assertEquals(ha.getNodeState(), ManagementNodeState.STANDBY);

        testResourceForcedFetch("/ha/method/ok", 200);
        testResourceForcedFetch("/ha/method/fail", 200);
        testResourceForcedFetch("/ha/class/fail", 200);

        ((ManagementContextInternal)getManagementContext()).terminate();
        assertEquals(ha.getNodeState(), ManagementNodeState.TERMINATED);

        testResourceForcedFetch("/ha/method/ok", 200);
        testResourceForcedFetch("/ha/method/fail", 200);
        testResourceForcedFetch("/ha/class/fail", 200);
    }


    private void testResourceFetch(String resourcePath, int code) {
        testResourceFetch(resourcePath, false, code);
    }

    private void testResourceForcedFetch(String resourcePath, int code) {
        testResourceFetch(resourcePath, true, code);
    }

    private void testResourceFetch(String resourcePath, boolean force, int code) {
        Builder resource = client().resource(resourcePath)
                .accept(MediaType.APPLICATION_JSON_TYPE);
        if (force) {
            resource.header(HaMasterCheckFilter.SKIP_CHECK_HEADER, "true");
        }
        ClientResponse response = resource
                .get(ClientResponse.class);
        assertEquals(response.getStatus(), code);
    }

}
