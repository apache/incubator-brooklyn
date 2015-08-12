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

import java.net.URI;

import org.apache.brooklyn.test.HttpTestUtils;
import org.apache.http.client.HttpClient;
import org.eclipse.jetty.server.Server;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.BasicApplication;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.basic.EntityPredicates;
import brooklyn.entity.proxying.EntitySpec;

import org.apache.brooklyn.management.ManagementContext;
import org.apache.brooklyn.rest.BrooklynRestApiLauncher;
import org.apache.brooklyn.rest.BrooklynRestApiLauncherTestFixture;
import org.apache.brooklyn.rest.testing.mocks.RestMockSimpleEntity;
import org.apache.brooklyn.test.HttpTestUtils;

import brooklyn.test.entity.LocalManagementContextForTests;
import brooklyn.util.collections.MutableList;
import brooklyn.util.http.HttpTool;
import brooklyn.util.http.HttpToolResponse;
import brooklyn.util.net.Urls;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;

public class SensorResourceIntegrationTest extends BrooklynRestApiLauncherTestFixture {

    private Server server;
    private ManagementContext mgmt;
    private BasicApplication app;

    @BeforeClass(alwaysRun = true)
    protected void setUp() {
        mgmt = LocalManagementContextForTests.newInstance();
        server = useServerForTest(BrooklynRestApiLauncher.launcher()
            .managementContext(mgmt)
            .withoutJsgui()
            .start());
        app = mgmt.getEntityManager().createEntity(EntitySpec.create(BasicApplication.class).displayName("simple-app")
            .child(EntitySpec.create(Entity.class, RestMockSimpleEntity.class).displayName("simple-ent")));
        mgmt.getEntityManager().manage(app);
        app.start(MutableList.of(mgmt.getLocationRegistry().resolve("localhost")));
    }
    
    // marked integration because of time
    @Test(groups = "Integration")
    public void testSensorBytes() throws Exception {
        EntityInternal entity = (EntityInternal) Iterables.find(mgmt.getEntityManager().getEntities(), EntityPredicates.displayNameEqualTo("simple-ent"));
        SensorResourceTest.addAmphibianSensor(entity);
        
        String baseUri = getBaseUri(server);
        URI url = URI.create(Urls.mergePaths(baseUri, SensorResourceTest.SENSORS_ENDPOINT, SensorResourceTest.SENSOR_NAME));
        
        // Uses explicit "application/json" because failed on jenkins as though "text/plain" was the default on Ubuntu jenkins! 
        HttpClient client = HttpTool.httpClientBuilder().uri(baseUri).build();
        HttpToolResponse response = HttpTool.httpGet(client, url, ImmutableMap.<String, String>of("Accept", "application/json"));
        HttpTestUtils.assertHealthyStatusCode(response.getResponseCode());
        Assert.assertEquals(response.getContentAsString(), "\"12345 frogs\"");
    }

}
