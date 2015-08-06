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

import java.util.List;

import org.apache.brooklyn.test.TestResourceUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.BrooklynAppUnitTestSupport;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.proxy.AbstractController;
import brooklyn.entity.proxy.LoadBalancer;
import brooklyn.entity.proxy.TrackingAbstractController;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.webapp.jboss.JBoss7Server;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.test.Asserts;
import brooklyn.test.EntityTestUtils;
import brooklyn.test.entity.TestJavaWebAppEntity;

import com.google.common.collect.ImmutableList;
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
