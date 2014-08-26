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

import java.net.URL;
import java.util.List;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.BasicConfigurableEntityFactory;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.entity.proxy.AbstractController;
import brooklyn.entity.proxy.LoadBalancer;
import brooklyn.entity.proxy.TrackingAbstractController;
import brooklyn.entity.proxy.nginx.NginxController;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.webapp.jboss.JBoss7Server;
import brooklyn.entity.webapp.tomcat.TomcatServer;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.test.Asserts;
import brooklyn.test.EntityTestUtils;
import brooklyn.test.HttpTestUtils;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestJavaWebAppEntity;
import brooklyn.util.collections.CollectionFunctionals;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.guava.Functionals;

import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

public class ControlledDynamicWebAppClusterTest {
    private static final Logger log = LoggerFactory.getLogger(ControlledDynamicWebAppClusterTest.class);

    private static final int TIMEOUT_MS = 10*1000;
    
    private URL warUrl;
    private TestApplication app;
    private LocalhostMachineProvisioningLocation loc;
    private List<LocalhostMachineProvisioningLocation> locs;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        String warPath = "hello-world.war";
        warUrl = getClass().getClassLoader().getResource(warPath);
        
        app = TestApplication.Factory.newManagedInstanceForTests();
        loc = new LocalhostMachineProvisioningLocation();
        locs = ImmutableList.of(loc);
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }
    
    @Test
    public void testUsesCustomController() {
        AbstractController controller = app.createAndManageChild(EntitySpec.create(TrackingAbstractController.class).displayName("mycustom"));

        ControlledDynamicWebAppCluster cluster = app.createAndManageChild(EntitySpec.create(ControlledDynamicWebAppCluster.class)
                .configure("initialSize", 0)
                .configure(ControlledDynamicWebAppCluster.CONTROLLER, controller)
                .configure("memberSpec", EntitySpec.create(JBoss7Server.class).configure("war", warUrl.toString())));
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
                .configure("memberSpec", EntitySpec.create(JBoss7Server.class).configure("war", warUrl.toString())));
        app.start(locs);
        LoadBalancer controller = cluster.getController();
        
        EntityTestUtils.assertAttributeEqualsEventually(controller, AbstractController.SERVICE_UP, true);
        assertEquals(controller.getDisplayName(), "mycustom");

        // Stopping cluster should stop the controller (because it created it)
        cluster.stop();
        EntityTestUtils.assertAttributeEquals(controller, AbstractController.SERVICE_UP, false);
    }
    
    @Test(groups="Integration")
    public void testConfiguresController() {
        ControlledDynamicWebAppCluster cluster = app.createAndManageChild(EntitySpec.create(ControlledDynamicWebAppCluster.class)
                .configure("initialSize", 1)
                .configure("memberSpec", EntitySpec.create(JBoss7Server.class).configure("war", warUrl.toString())));
        app.start(locs);

        String url = cluster.getController().getAttribute(NginxController.ROOT_URL);
        HttpTestUtils.assertHttpStatusCodeEventuallyEquals(url, 200);
        HttpTestUtils.assertContentEventuallyContainsText(url, "Hello");
    }
    
    @Test
    public void testSetsInitialSize() {
        ControlledDynamicWebAppCluster cluster = app.createAndManageChild(EntitySpec.create(ControlledDynamicWebAppCluster.class)
                .configure("initialSize", 2)
                .configure(ControlledDynamicWebAppCluster.CONTROLLER_SPEC, EntitySpec.create(TrackingAbstractController.class))
                .configure("factory", new BasicConfigurableEntityFactory<TestJavaWebAppEntity>(TestJavaWebAppEntity.class)));
        app.start(locs);

        Iterable<TestJavaWebAppEntity> webservers = Iterables.filter(cluster.getCluster().getMembers(), TestJavaWebAppEntity.class);
        assertEquals(Iterables.size(webservers), 2, "webservers="+webservers);
    }
    
    @Test(groups="Integration")
    public void testSetsToplevelHostnameFromController() {
        ControlledDynamicWebAppCluster cluster = app.createAndManageChild(EntitySpec.create(ControlledDynamicWebAppCluster.class)
                .configure("initialSize", 1)
                .configure("memberSpec", EntitySpec.create(JBoss7Server.class).configure("war", warUrl.toString())));
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
    
    @Test(groups="Integration")
    public void testCustomWebClusterSpecGetsMemberSpec() {
        ControlledDynamicWebAppCluster cluster = app.createAndManageChild(EntitySpec.create(ControlledDynamicWebAppCluster.class)
                .configure("initialSize", 1)
                .configure(ControlledDynamicWebAppCluster.MEMBER_SPEC, EntitySpec.create(JBoss7Server.class)
                        .configure(JBoss7Server.ROOT_WAR, warUrl.toString()))
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
        ControlledDynamicWebAppCluster cluster = app.createAndManageChild(EntitySpec.create(ControlledDynamicWebAppCluster.class)
                .configure("initialSize", 1)
                .configure("factory", new BasicConfigurableEntityFactory<TestJavaWebAppEntity>(TestJavaWebAppEntity.class)));
        
        EntityTestUtils.assertAttributeEqualsEventually(cluster, Attributes.SERVICE_STATE_ACTUAL, Lifecycle.STOPPED);
        
        RecordingSensorEventListener<Lifecycle> listener = new RecordingSensorEventListener<Lifecycle>(true);
        app.subscribe(cluster, Attributes.SERVICE_STATE_ACTUAL, listener);
        app.start(locs);
        
        Asserts.eventually(Suppliers.ofInstance(listener.getValues()), CollectionFunctionals.sizeEquals(2));
        assertEquals(listener.getValues(), ImmutableList.of(Lifecycle.STARTING, Lifecycle.RUNNING), "vals="+listener.getValues());
        listener.getValues().clear();
        
        app.stop();
        EntityTestUtils.assertAttributeEqualsEventually(cluster, Attributes.SERVICE_STATE_ACTUAL, Lifecycle.STOPPED);
        Asserts.eventually(Suppliers.ofInstance(listener.getValues()), CollectionFunctionals.sizeEquals(2));
        assertEquals(listener.getValues(), ImmutableList.of(Lifecycle.STOPPING, Lifecycle.STOPPED), "vals="+listener.getValues());
    }
    
    @Test(groups="Integration")
    public void testTomcatAbsoluteRedirect() {
        final ControlledDynamicWebAppCluster cluster = app.createAndManageChild(EntitySpec.create(ControlledDynamicWebAppCluster.class)
            .configure(ControlledDynamicWebAppCluster.MEMBER_SPEC, EntitySpec.create(TomcatServer.class)
                    .configure(TomcatServer.ROOT_WAR, "classpath://hello-world.war"))
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
    
    public static class RecordingSensorEventListener<T> implements SensorEventListener<T> {
        private final List<SensorEvent<T>> events = Lists.newCopyOnWriteArrayList();
        private final List<T> values = Lists.newCopyOnWriteArrayList();
        private boolean skipDuplicateValues;

        public RecordingSensorEventListener() {
            this(false);
        }
        
        public RecordingSensorEventListener(boolean skipDuplicateValues) {
            this.skipDuplicateValues = skipDuplicateValues;
        }
        
        @Override
        public void onEvent(SensorEvent<T> event) {
            events.add(event);
            if (skipDuplicateValues && !values.isEmpty() && values.get(values.size()-1).equals(event.getValue())) {
                // skip
            } else {
                values.add(event.getValue());
            }
        }
        
        public List<SensorEvent<T>> getEvents() {
            return events;
        }
        
        public List<T> getValues() {
            return values;
        }
    }
    
    @Test
    public void testMembersReflectChildClusterMembers() {
        final ControlledDynamicWebAppCluster cluster = app.createAndManageChild(EntitySpec.create(ControlledDynamicWebAppCluster.class)
                .configure("initialSize", 1)
                .configure(ControlledDynamicWebAppCluster.CONTROLLER_SPEC, EntitySpec.create(TrackingAbstractController.class))
                .configure("factory", new BasicConfigurableEntityFactory<TestJavaWebAppEntity>(TestJavaWebAppEntity.class)));
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
                .configure("factory", new BasicConfigurableEntityFactory<TestJavaWebAppEntity>(TestJavaWebAppEntity.class)));
        app.start(locs);
        final DynamicWebAppCluster childCluster = cluster.getCluster();
        LoadBalancer controller = cluster.getController();
        
        Entities.unmanage(childCluster);
        Entities.unmanage(controller);
        
        cluster.stop();
        EntityTestUtils.assertAttributeEquals(cluster, ControlledDynamicWebAppCluster.SERVICE_STATE_ACTUAL, Lifecycle.STOPPED);
    }
}
