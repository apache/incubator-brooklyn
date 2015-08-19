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
package org.apache.brooklyn.entity.webapp;

import static org.testng.Assert.assertEquals;

import java.net.URI;
import java.util.Collection;

import org.apache.http.client.HttpClient;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.apache.brooklyn.location.localhost.LocalhostMachineProvisioningLocation;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.location.LocationSpec;
import org.apache.brooklyn.api.mgmt.Task;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.entity.BrooklynConfigKeys;
import org.apache.brooklyn.core.test.BrooklynAppUnitTestSupport;
import org.apache.brooklyn.entity.webapp.JavaWebAppSoftwareProcess;
import org.apache.brooklyn.entity.webapp.jboss.JBoss7Server;
import org.apache.brooklyn.entity.webapp.tomcat.TomcatServer;
import org.apache.brooklyn.test.Asserts;
import org.apache.brooklyn.test.EntityTestUtils;
import org.apache.brooklyn.test.support.TestResourceUnavailableException;
import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.core.http.HttpTool;
import org.apache.brooklyn.util.core.http.HttpToolResponse;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class WebAppConcurrentDeployTest extends BrooklynAppUnitTestSupport {
    private Location loc;
    
    @Override
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        super.setUp();
        app.config().set(BrooklynConfigKeys.SKIP_ON_BOX_BASE_DIR_RESOLUTION, false);
//      tested on  loc = mgmt.getLocationRegistry().resolve("byon:(hosts=\"hostname\")");
        loc = mgmt.getLocationManager().createLocation(LocationSpec.create(LocalhostMachineProvisioningLocation.class));
    }
    
    @DataProvider(name = "basicEntities")
    public Object[][] basicEntities() {
        return new Object[][]{
            {EntitySpec.create(TomcatServer.class)},
            // Hot Deploy not enabled?
            // {EntitySpec.create(JBoss6Server.class)},
            {EntitySpec.create(JBoss7Server.class)},
        };
    }

    @Test(groups = "Live", dataProvider="basicEntities")
    public void testConcurrentDeploys(EntitySpec<? extends JavaWebAppSoftwareProcess> webServerSpec) throws Exception {
        JavaWebAppSoftwareProcess server = app.createAndManageChild(webServerSpec);
        app.start(ImmutableList.of(loc));
        EntityTestUtils.assertAttributeEqualsEventually(server, Attributes.SERVICE_UP, Boolean.TRUE);
        Collection<Task<Void>> deploys = MutableList.of();
        for (int i = 0; i < 5; i++) {
            deploys.add(server.invoke(TomcatServer.DEPLOY, MutableMap.of("url", getTestWar(), "targetName", "/")));
        }
        for(Task<Void> t : deploys) {
            t.getUnchecked();
        }

        final HttpClient client = HttpTool.httpClientBuilder().build();
        final URI warUrl = URI.create(server.getAttribute(JavaWebAppSoftwareProcess.ROOT_URL));
        Asserts.succeedsEventually(new Runnable() {
            @Override
            public void run() {
                HttpToolResponse resp = HttpTool.httpGet(client, warUrl, ImmutableMap.<String,String>of());
                assertEquals(resp.getResponseCode(), 200);
            }
        });
    }
    
    public String getTestWar() {
        TestResourceUnavailableException.throwIfResourceUnavailable(getClass(), "/hello-world.war");
        return "classpath://hello-world.war";
    }

}
