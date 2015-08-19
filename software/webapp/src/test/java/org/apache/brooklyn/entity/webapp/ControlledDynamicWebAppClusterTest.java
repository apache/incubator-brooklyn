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

import java.util.List;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.entity.Group;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.lifecycle.Lifecycle;
import org.apache.brooklyn.core.test.BrooklynAppUnitTestSupport;
import org.apache.brooklyn.entity.group.BasicGroup;
import org.apache.brooklyn.entity.proxy.AbstractController;
import org.apache.brooklyn.entity.proxy.LoadBalancer;
import org.apache.brooklyn.entity.proxy.TrackingAbstractController;
import org.apache.brooklyn.entity.software.base.SoftwareProcess;
import org.apache.brooklyn.entity.webapp.jboss.JBoss7Server;
import org.apache.brooklyn.test.Asserts;
import org.apache.brooklyn.test.EntityTestUtils;
import org.apache.brooklyn.test.entity.TestJavaWebAppEntity;
import org.apache.brooklyn.test.support.TestResourceUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.apache.brooklyn.location.localhost.LocalhostMachineProvisioningLocation;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

public class ControlledDynamicWebAppClusterTest extends BrooklynAppUnitTestSupport {
    private static final Logger log = LoggerFactory.getLogger(ControlledDynamicWebAppClusterTest.class);

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

    @Test
    public void testUsesCustomController() {
        AbstractController controller = app.createAndManageChild(EntitySpec.create(TrackingAbstractController.class).displayName("mycustom"));

        ControlledDynamicWebAppCluster cluster = app.createAndManageChild(EntitySpec.create(ControlledDynamicWebAppCluster.class)
                .configure("initialSize", 0)
                .configure(ControlledDynamicWebAppCluster.CONTROLLER, controller)
                .configure("memberSpec", EntitySpec.create(JBoss7Server.class).configure("war", getTestWar())));
        app.start(locs);

        EntityTestUtils.assertAttributeEqualsEventually(controller, AbstractController.SERVICE_UP, true);
        assertEquals(cluster.getController(), controller);

        // Stopping cluster should not stop controller (because it didn't create it)
        cluster.stop();
        EntityTestUtils.assertAttributeEquals(controller, AbstractController.SERVICE_UP, true);
    }
    
    @Test
    public void testUsesCustomControlledGroup() {
        TestJavaWebAppEntity webServer = app.createAndManageChild(EntitySpec.create(TestJavaWebAppEntity.class));
        webServer.setAttribute(Attributes.SUBNET_HOSTNAME, "myhostname");
        webServer.setAttribute(Attributes.HTTP_PORT, 1234);
        
        TrackingAbstractController controller = app.createAndManageChild(EntitySpec.create(TrackingAbstractController.class));
        Group controlledGroup = app.createAndManageChild(EntitySpec.create(BasicGroup.class));
        controlledGroup.addMember(webServer);
        
        ControlledDynamicWebAppCluster cluster = app.createAndManageChild(EntitySpec.create(ControlledDynamicWebAppCluster.class)
                .configure("initialSize", 0)
                .configure(ControlledDynamicWebAppCluster.CONTROLLER, controller)
                .configure(ControlledDynamicWebAppCluster.CONTROLLED_GROUP, controlledGroup)
                .configure("memberSpec", EntitySpec.create(JBoss7Server.class).configure("war", getTestWar())));
        app.start(locs);

        assertEquals(controller.getUpdates(), ImmutableList.of(ImmutableSet.of("myhostname:1234")));
    }
    
    @Test
    public void testUsesCustomControllerSpec() {
        EntitySpec<TrackingAbstractController> controllerSpec = EntitySpec.create(TrackingAbstractController.class).displayName("mycustom");
        ControlledDynamicWebAppCluster cluster = app.createAndManageChild(EntitySpec.create(ControlledDynamicWebAppCluster.class)
                .configure("initialSize", 0)
                .configure(ControlledDynamicWebAppCluster.CONTROLLER_SPEC, controllerSpec)
                .configure("memberSpec", EntitySpec.create(JBoss7Server.class).configure("war", getTestWar())));
        app.start(locs);
        LoadBalancer controller = cluster.getController();
        
        EntityTestUtils.assertAttributeEqualsEventually(controller, AbstractController.SERVICE_UP, true);
        assertEquals(controller.getDisplayName(), "mycustom");

        // Stopping cluster should stop the controller (because it created it)
        cluster.stop();
        EntityTestUtils.assertAttributeEquals(controller, AbstractController.SERVICE_UP, false);
    }
    
    @Test
    public void testTheTestJavaWebApp() {
        SoftwareProcess n = app.createAndManageChild(EntitySpec.create(TestJavaWebAppEntity.class));
        app.start(locs);

        EntityTestUtils.assertAttributeEqualsEventually(n, AbstractController.SERVICE_UP, true);
        
        app.stop();
        EntityTestUtils.assertAttributeEqualsEventually(n, AbstractController.SERVICE_UP, false);
    }
    
    @Test
    public void testSetsInitialSize() {
        ControlledDynamicWebAppCluster cluster = app.createAndManageChild(EntitySpec.create(ControlledDynamicWebAppCluster.class)
                .configure("initialSize", 2)
                .configure(ControlledDynamicWebAppCluster.CONTROLLER_SPEC, EntitySpec.create(TrackingAbstractController.class))
                .configure(ControlledDynamicWebAppCluster.MEMBER_SPEC, EntitySpec.create(TestJavaWebAppEntity.class)) );
        app.start(locs);

        Iterable<TestJavaWebAppEntity> webservers = Iterables.filter(cluster.getCluster().getMembers(), TestJavaWebAppEntity.class);
        assertEquals(Iterables.size(webservers), 2, "webservers="+webservers);
    }
    
    @Test
    public void testUsesCustomWebClusterSpec() {
        ControlledDynamicWebAppCluster cluster = app.createAndManageChild(EntitySpec.create(ControlledDynamicWebAppCluster.class)
                .configure("initialSize", 0)
                .configure(ControlledDynamicWebAppCluster.CONTROLLER_SPEC, EntitySpec.create(TrackingAbstractController.class))
                .configure(ControlledDynamicWebAppCluster.WEB_CLUSTER_SPEC, EntitySpec.create(DynamicWebAppCluster.class)
                        .displayName("mydisplayname")));
        app.start(locs);

        assertEquals(cluster.getCluster().getDisplayName(), "mydisplayname");
    }

    @Test
    public void testMembersReflectChildClusterMembers() {
        final ControlledDynamicWebAppCluster cluster = app.createAndManageChild(EntitySpec.create(ControlledDynamicWebAppCluster.class)
                .configure("initialSize", 1)
                .configure(ControlledDynamicWebAppCluster.CONTROLLER_SPEC, EntitySpec.create(TrackingAbstractController.class))
                .configure(ControlledDynamicWebAppCluster.MEMBER_SPEC, EntitySpec.create(TestJavaWebAppEntity.class)) );
        app.start(locs);
        final DynamicWebAppCluster childCluster = cluster.getCluster();
        
        // Expect initial member(s) to be the same
        assertEquals(childCluster.getMembers().size(), 1);
        Asserts.succeedsEventually(new Runnable() {
            @Override public void run() {
                Asserts.assertEqualsIgnoringOrder(childCluster.getMembers(), cluster.getMembers());
            }});
        
        // After resize up, same members
        cluster.resize(2);
        assertEquals(childCluster.getMembers().size(), 2);
        Asserts.succeedsEventually(new Runnable() {
            @Override public void run() {
                Asserts.assertEqualsIgnoringOrder(childCluster.getMembers(), cluster.getMembers());
            }});
        
        // After resize down, same members
        cluster.resize(1);
        assertEquals(childCluster.getMembers().size(), 1);
        Asserts.succeedsEventually(new Runnable() {
            @Override public void run() {
                Asserts.assertEqualsIgnoringOrder(childCluster.getMembers(), cluster.getMembers());
            }});
    }
    
    @Test
    public void testStopOnChildUnmanaged() {
        final ControlledDynamicWebAppCluster cluster = app.createAndManageChild(EntitySpec.create(ControlledDynamicWebAppCluster.class)
                .configure("initialSize", 1)
                .configure(ControlledDynamicWebAppCluster.CONTROLLER_SPEC, EntitySpec.create(TrackingAbstractController.class))
                .configure(ControlledDynamicWebAppCluster.MEMBER_SPEC, EntitySpec.create(TestJavaWebAppEntity.class)) );
        app.start(locs);
        final DynamicWebAppCluster childCluster = cluster.getCluster();
        LoadBalancer controller = cluster.getController();
        
        Entities.unmanage(childCluster);
        Entities.unmanage(controller);
        
        cluster.stop();
        EntityTestUtils.assertAttributeEquals(cluster, ControlledDynamicWebAppCluster.SERVICE_STATE_ACTUAL, Lifecycle.STOPPED);
    }
}
