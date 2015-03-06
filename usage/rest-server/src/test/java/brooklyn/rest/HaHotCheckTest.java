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
package brooklyn.rest;

import static org.testng.Assert.assertEquals;

import javax.ws.rs.core.MediaType;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.management.ha.HighAvailabilityManager;
import brooklyn.management.ha.ManagementNodeState;
import brooklyn.rest.filter.HaHotCheckResourceFilter;
import brooklyn.rest.testing.BrooklynRestResourceTest;
import brooklyn.rest.testing.mocks.ManagementContextMock;
import brooklyn.rest.util.HaHotStateCheckClassResource;
import brooklyn.rest.util.HaHotStateCheckResource;
import brooklyn.rest.util.ManagementContextProvider;

import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.core.ResourceConfig;

public class HaHotCheckTest extends BrooklynRestResourceTest {

    private ManagementContextMock mgmtMock;

    @Override
    @BeforeMethod(alwaysRun = true)
    public void setUp() throws Exception {
        mgmtMock = new ManagementContextMock();
        super.setUp();
    }

    @Override
    protected void addBrooklynResources() {
        config.getSingletons().add(new ManagementContextProvider(mgmtMock));
        config.getProperties().put(ResourceConfig.PROPERTY_RESOURCE_FILTER_FACTORIES, HaHotCheckResourceFilter.class.getName());
        addResource(new HaHotStateCheckResource());
        addResource(new HaHotStateCheckClassResource());
    }

    @Test
    public void testHaCheck() {
        HighAvailabilityManager ha = mgmtMock.getHighAvailabilityManager();
        assertEquals(ha.getNodeState(), ManagementNodeState.MASTER);
        testResourceFetch("/ha/method/ok", 200);
        testResourceFetch("/ha/method/fail", 200);
        testResourceFetch("/ha/class/fail", 200);

        mgmtMock.setState(ManagementNodeState.STANDBY);
        assertEquals(ha.getNodeState(), ManagementNodeState.STANDBY);

        testResourceFetch("/ha/method/ok", 200);
        testResourceFetch("/ha/method/fail", 403);
        testResourceFetch("/ha/class/fail", 403);

        //forces isRunning = false
        mgmtMock.setState(ManagementNodeState.TERMINATED);
        assertEquals(ha.getNodeState(), ManagementNodeState.TERMINATED);

        testResourceFetch("/ha/method/ok", 200);
        testResourceFetch("/ha/method/fail", 200);
        testResourceFetch("/ha/class/fail", 200);
    }

    private void testResourceFetch(String resource, int code) {
        ClientResponse response = client().resource(resource)
                .accept(MediaType.APPLICATION_JSON_TYPE)
                .get(ClientResponse.class);
        assertEquals(response.getStatus(), code);
    }

}
