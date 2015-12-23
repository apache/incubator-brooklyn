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
package org.apache.brooklyn.entity.nosql.hazelcast;

import static org.testng.Assert.assertEquals;

import java.net.URISyntaxException;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.EntityAsserts;
import org.apache.brooklyn.core.entity.trait.Startable;
import org.apache.brooklyn.core.test.entity.TestApplication;
import org.apache.brooklyn.util.http.HttpTool;
import org.apache.brooklyn.util.http.HttpToolResponse;
import org.apache.http.client.methods.HttpGet;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.apache.brooklyn.location.localhost.LocalhostMachineProvisioningLocation;

import com.google.common.collect.ImmutableList;

public class HazelcastNodeIntegrationTest {
    protected TestApplication app;
    protected Location testLocation;
    protected HazelcastNode hazelcastNode;

    @BeforeMethod(alwaysRun = true)
    public void setup() throws Exception {
        app = TestApplication.Factory.newManagedInstanceForTests();;
        testLocation = new LocalhostMachineProvisioningLocation();
    }

    @AfterMethod(alwaysRun = true)
    public void shutdown() {
        Entities.destroyAll(app.getManagementContext());
    }
    
    @Test(groups = {"Integration"})
    public void testHazelcastStartupAndShutdown() {
        hazelcastNode = app.createAndManageChild(EntitySpec.create(HazelcastNode.class));
        app.start(ImmutableList.of(testLocation));
        EntityAsserts.assertAttributeEqualsEventually(hazelcastNode, Startable.SERVICE_UP, true);

        hazelcastNode.stop();
        EntityAsserts.assertAttributeEqualsEventually(hazelcastNode, Startable.SERVICE_UP, false);
    }

    @Test(groups = {"Integration"})
    public void testHazelcastRestInterface() throws URISyntaxException {
        hazelcastNode = app.createAndManageChild(EntitySpec.create(HazelcastNode.class));
        app.start(ImmutableList.of(testLocation));

        EntityAsserts.assertAttributeEqualsEventually(hazelcastNode, Startable.SERVICE_UP, true);
        EntityAsserts.assertAttributeEquals(hazelcastNode, HazelcastNode.NODE_PORT, 5701);

        String baseUri = String.format("http://%s:%d/hazelcast/rest/cluster", hazelcastNode.getAttribute(Attributes.HOSTNAME), hazelcastNode.getAttribute(HazelcastNode.NODE_PORT)); 
        HttpToolResponse response = HttpTool.execAndConsume(
                HttpTool.httpClientBuilder().build(),
                new HttpGet(baseUri));
        assertEquals(response.getResponseCode(), 200);
    }

    @Test(groups = {"Integration"})
    public void testHazelcastClient() throws URISyntaxException {
        hazelcastNode = app.createAndManageChild(EntitySpec.create(HazelcastNode.class));
        app.start(ImmutableList.of(testLocation));

        EntityAsserts.assertAttributeEqualsEventually(hazelcastNode, Startable.SERVICE_UP, true);
        HazelcastTestHelper helper = new HazelcastTestHelper(hazelcastNode.getAttribute(Attributes.HOSTNAME), hazelcastNode.getAttribute(HazelcastNode.NODE_PORT));

        HazelcastInstance client = helper.getClient();
        HazelcastInstance client2 = helper.getClient();

        client.getMap(HazelcastTestHelper.GROUP_NAME).put("A", "a");
        client2.getMap(HazelcastTestHelper.GROUP_NAME).put("B", "b");

        final IMap<Object, Object> map = client.getMap(HazelcastTestHelper.GROUP_NAME);
        assertEquals("a", map.get("A"));
        assertEquals("b", map.get("B"));

        client.shutdown();
        client2.shutdown();
    }
}
