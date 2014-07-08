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
package brooklyn.entity.proxy.nginx;

import static org.testng.Assert.assertEquals;

import java.net.URL;
import java.util.Map;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.BasicConfigurableEntityFactory;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityFactory;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.proxy.StubAppServer;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.test.Asserts;
import brooklyn.test.entity.TestApplication;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;

public class NginxLightIntegrationTest {

    private TestApplication app;
    private NginxController nginx;
    private DynamicCluster cluster;

    private URL war;
    private static String WAR_URL = "classpath://hello-world.war";
    
    @BeforeMethod
    public void setup() {
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
    }

    @AfterMethod(alwaysRun=true)
    public void shutdown() {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }

    // FIXME Fails because getting addEntity callback for group members while nginx is still starting,
    // so important nginx fields are still null. Therefore get NPE for cluster members, and thus targets
    // is of size zero.
    @Test(groups = {"Integration", "WIP"})
    public void testNginxTargetsMatchesClusterMembers() {
        EntityFactory<StubAppServer> serverFactory = new BasicConfigurableEntityFactory<StubAppServer>(StubAppServer.class);
        final DynamicCluster cluster = app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure("initialSize", 2)
                .configure("factory", serverFactory));
                
        nginx = app.createAndManageChild(EntitySpec.create(NginxController.class)
                .configure("serverPool", cluster)
                .configure("domain", "localhost"));
        
        app.start(ImmutableList.of(new LocalhostMachineProvisioningLocation()));
        
        // Wait for url-mapping to update its TARGET_ADDRESSES (through async subscription)
        Asserts.succeedsEventually(new Runnable() {
            @Override public void run() {
                Map<Entity, String> expectedTargets = Maps.newLinkedHashMap();
                for (Entity member : cluster.getMembers()) {
                    expectedTargets.put(member, member.getAttribute(Attributes.HOSTNAME)+":"+member.getAttribute(Attributes.HTTP_PORT));
                }                
                assertEquals(nginx.getAttribute(NginxController.SERVER_POOL_TARGETS).size(), 2);
                assertEquals(nginx.getAttribute(NginxController.SERVER_POOL_TARGETS), expectedTargets);
            }});
    }
}
