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
package brooklyn.entity.webapp;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import java.util.List;
import java.util.concurrent.Callable;

import org.apache.brooklyn.test.TestResourceUnavailableException;
import org.apache.brooklyn.entity.basic.RecordingSensorEventListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.BrooklynAppLiveTestSupport;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.entity.proxy.AbstractController;
import brooklyn.entity.proxy.LoadBalancer;
import brooklyn.entity.proxy.nginx.NginxController;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.webapp.tomcat.TomcatServer;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.test.Asserts;
import brooklyn.test.EntityTestUtils;
import brooklyn.test.HttpTestUtils;
import brooklyn.test.entity.TestJavaWebAppEntity;
import brooklyn.util.collections.CollectionFunctionals;
import brooklyn.util.collections.MutableMap;

import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

public class ControlledDynamicWebAppClusterIntegrationTest extends BrooklynAppLiveTestSupport {
    private static final Logger log = LoggerFactory.getLogger(ControlledDynamicWebAppClusterIntegrationTest.class);

    private static final int TIMEOUT_MS = 10*1000;
    
    private LocalhostMachineProvisioningLocation loc;
    private List<LocalhostMachineProvisioningLocation> locs;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        super.setUp();

        loc = app.newLocalhostProvisioningLocation();
        locs = ImmutableList.of(loc);
    }

    public String getTestWar() {
        TestResourceUnavailableException.throwIfResourceUnavailable(getClass(), "/hello-world.war");
        return "classpath://hello-world.war";
    }
    
    @Test(groups="Integration")
    public void testPropogateHttpPorts() {
        ControlledDynamicWebAppCluster cluster = app.createAndManageChild(EntitySpec.create(ControlledDynamicWebAppCluster.class)
                .configure("initialSize", 1));
        app.start(locs);

        EntityTestUtils.assertAttributeEventuallyNonNull(cluster, LoadBalancer.PROXY_HTTP_PORT);
        EntityTestUtils.assertAttributeEventuallyNonNull(cluster, LoadBalancer.PROXY_HTTPS_PORT);
    }
    
    @Test(groups="Integration")
    public void testConfiguresController() {
        ControlledDynamicWebAppCluster cluster = app.createAndManageChild(EntitySpec.create(ControlledDynamicWebAppCluster.class)
                .configure("initialSize", 1)
                .configure("memberSpec", EntitySpec.create(TomcatServer.class).configure("war", getTestWar())));
        app.start(locs);

        String url = cluster.getController().getAttribute(NginxController.ROOT_URL);
        HttpTestUtils.assertHttpStatusCodeEventuallyEquals(url, 200);
        HttpTestUtils.assertContentEventuallyContainsText(url, "Hello");
    }
    
    @Test(groups="Integration")
    public void testSetsToplevelHostnameFromController() {
        ControlledDynamicWebAppCluster cluster = app.createAndManageChild(EntitySpec.create(ControlledDynamicWebAppCluster.class)
                .configure("initialSize", 1)
                .configure("memberSpec", EntitySpec.create(TomcatServer.class).configure("war", getTestWar())));
        app.start(locs);

        String expectedHostname = cluster.getController().getAttribute(LoadBalancer.HOSTNAME);
        String expectedRootUrl = cluster.getController().getAttribute(LoadBalancer.ROOT_URL);
        boolean expectedServiceUp = true;
        
        assertNotNull(expectedHostname);
        assertNotNull(expectedRootUrl);
        
        EntityTestUtils.assertAttributeEqualsEventually(MutableMap.of("timeout", TIMEOUT_MS), cluster, ControlledDynamicWebAppCluster.HOSTNAME, expectedHostname);
        EntityTestUtils.assertAttributeEqualsEventually(MutableMap.of("timeout", TIMEOUT_MS), cluster, ControlledDynamicWebAppCluster.ROOT_URL, expectedRootUrl);
        EntityTestUtils.assertAttributeEqualsEventually(MutableMap.of("timeout", TIMEOUT_MS), cluster, ControlledDynamicWebAppCluster.SERVICE_UP, expectedServiceUp);
    }
    
    @Test(groups="Integration")
    public void testCustomWebClusterSpecGetsMemberSpec() {
        ControlledDynamicWebAppCluster cluster = app.createAndManageChild(EntitySpec.create(ControlledDynamicWebAppCluster.class)
                .configure("initialSize", 1)
                .configure(ControlledDynamicWebAppCluster.MEMBER_SPEC, EntitySpec.create(TomcatServer.class)
                        .configure(TomcatServer.ROOT_WAR, getTestWar()))
                .configure(ControlledDynamicWebAppCluster.WEB_CLUSTER_SPEC, EntitySpec.create(DynamicWebAppCluster.class)
                        .displayName("mydisplayname")));
        app.start(locs);

        String url = cluster.getController().getAttribute(NginxController.ROOT_URL);
        HttpTestUtils.assertContentEventuallyContainsText(url, "Hello");

        // and make sure it really was using our custom spec
        assertEquals(cluster.getCluster().getDisplayName(), "mydisplayname");
    }
    
    // Needs to be integration test because still using nginx controller; could pass in mock controller
    @Test(groups="Integration")
    public void testSetsServiceLifecycle() {
        ControlledDynamicWebAppCluster cluster = app.createAndManageChild( EntitySpec.create(ControlledDynamicWebAppCluster.class)
                .configure("initialSize", 1)
                .configure(ControlledDynamicWebAppCluster.MEMBER_SPEC, EntitySpec.create(TestJavaWebAppEntity.class)) );
        
        EntityTestUtils.assertAttributeEqualsEventually(cluster, Attributes.SERVICE_STATE_ACTUAL, Lifecycle.STOPPED);
        
        RecordingSensorEventListener<Lifecycle> listener = new RecordingSensorEventListener<Lifecycle>(true);
        app.subscribe(cluster, Attributes.SERVICE_STATE_ACTUAL, listener);
        app.start(locs);
        
        Asserts.eventually(Suppliers.ofInstance(listener.getEventValues()), CollectionFunctionals.sizeEquals(2));
        assertEquals(listener.getEventValues(), ImmutableList.of(Lifecycle.STARTING, Lifecycle.RUNNING), "vals="+listener.getEventValues());
        listener.clearEvents();
        
        app.stop();
        EntityTestUtils.assertAttributeEqualsEventually(cluster, Attributes.SERVICE_STATE_ACTUAL, Lifecycle.STOPPED);
        Asserts.eventually(Suppliers.ofInstance(listener), CollectionFunctionals.sizeEquals(2));
        assertEquals(listener.getEventValues(), ImmutableList.of(Lifecycle.STOPPING, Lifecycle.STOPPED), "vals="+listener.getEventValues());
    }
    
    @Test(groups="Integration")
    public void testTomcatAbsoluteRedirect() {
        final ControlledDynamicWebAppCluster cluster = app.createAndManageChild(EntitySpec.create(ControlledDynamicWebAppCluster.class)
            .configure(ControlledDynamicWebAppCluster.MEMBER_SPEC, EntitySpec.create(TomcatServer.class)
            .configure(TomcatServer.ROOT_WAR, getTestWar()))
            .configure("initialSize", 1)
            .configure(AbstractController.SERVICE_UP_URL_PATH, "hello/redirectAbsolute")
        );
        app.start(locs);

        final NginxController nginxController = (NginxController) cluster.getController();
        Asserts.succeedsEventually(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                return nginxController.getServerPoolAddresses().size() == 1;
            }
        });
        
        Entity tomcatServer = Iterables.getOnlyElement(cluster.getCluster().getMembers());
        EntityTestUtils.assertAttributeEqualsEventually(tomcatServer, Attributes.SERVICE_UP, true);
        
        EntityTestUtils.assertAttributeEqualsContinually(nginxController, Attributes.SERVICE_UP, true);
        
        app.stop();
    }
}
