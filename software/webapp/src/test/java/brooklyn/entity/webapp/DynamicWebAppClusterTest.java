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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.BasicConfigurableEntityFactory;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.trait.Startable;
import brooklyn.location.basic.SimulatedLocation;
import brooklyn.test.Asserts;
import brooklyn.test.EntityTestUtils;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestJavaWebAppEntity;
import brooklyn.util.collections.MutableMap;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;

/**
 * TODO clarify test purpose
 */
public class DynamicWebAppClusterTest {
    private static final Logger log = LoggerFactory.getLogger(DynamicWebAppClusterTest.class);
    
    private static final int TIMEOUT_MS = 1*1000;
    private static final int SHORT_WAIT_MS = 250;
    
    private TestApplication app;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }

    @Test
    public void testTestJavaWebAppEntity() throws Exception {
        Entity test = app.createAndManageChild(EntitySpec.create(Entity.class, TestJavaWebAppEntity.class));
        test.invoke(Startable.START, ImmutableMap.of("locations", ImmutableList.of())).get();
    }
    
    @Test
    public void testRequestCountAggregation() throws Exception {
        final DynamicWebAppCluster cluster = app.createAndManageChild(EntitySpec.create(DynamicWebAppCluster.class)
                .configure("initialSize", 2)
                .configure("factory", new BasicConfigurableEntityFactory<TestJavaWebAppEntity>(TestJavaWebAppEntity.class)));
        
        app.start(ImmutableList.of(new SimulatedLocation()));
        
        for (Entity member : cluster.getMembers()) {
            ((TestJavaWebAppEntity)member).spoofRequest();
        }
        
        Asserts.succeedsEventually(new Runnable() {
            @Override public void run() {
                // intermittent failure observed 4 may 2012
                assertEquals(cluster.getAttribute(DynamicWebAppCluster.REQUEST_COUNT), (Integer)2);
            }});
        
        for (Entity member : cluster.getMembers()) {
            for (int i = 0; i < 2; i++) {
                ((TestJavaWebAppEntity)member).spoofRequest();
            }
        }
        Asserts.succeedsEventually(new Runnable() {
            @Override public void run() {
                // intermittent failure observed 4 may 2012
                assertEquals(cluster.getAttribute(DynamicWebAppCluster.REQUEST_COUNT_PER_NODE), (Double)3d);
            }});
    }
    
    @Test
    public void testSetsServiceUpIfMemberIsUp() throws Exception {
        DynamicWebAppCluster cluster = app.createAndManageChild(EntitySpec.create(DynamicWebAppCluster.class)
                .configure("initialSize", 1)
                .configure("factory", new BasicConfigurableEntityFactory<TestJavaWebAppEntity>(TestJavaWebAppEntity.class)));
    
        app.start(ImmutableList.of(new SimulatedLocation()));
        
        // Should initially be true (now that TestJavaWebAppEntity sets true) 
        EntityTestUtils.assertAttributeEqualsEventually(MutableMap.of("timeout", TIMEOUT_MS), cluster, DynamicWebAppCluster.SERVICE_UP, true);
        
        // When child is !service_up, should report false
        ((EntityLocal)Iterables.get(cluster.getMembers(), 0)).setAttribute(Startable.SERVICE_UP, false);
        EntityTestUtils.assertAttributeEqualsEventually(MutableMap.of("timeout", TIMEOUT_MS), cluster, DynamicWebAppCluster.SERVICE_UP, false);
        EntityTestUtils.assertAttributeEqualsContinually(MutableMap.of("timeout", SHORT_WAIT_MS), cluster, DynamicWebAppCluster.SERVICE_UP, false);
        
        cluster.resize(2);
        
        // When one of the two children is service_up, should report true
        EntityTestUtils.assertAttributeEqualsEventually(MutableMap.of("timeout", TIMEOUT_MS), cluster, DynamicWebAppCluster.SERVICE_UP, true);

        // And if that serviceUp child goes away, should again report false
        Entities.unmanage(Iterables.get(cluster.getMembers(), 1));
        ((EntityLocal)Iterables.get(cluster.getMembers(), 0)).setAttribute(Startable.SERVICE_UP, false);
        
        EntityTestUtils.assertAttributeEqualsEventually(MutableMap.of("timeout", TIMEOUT_MS), cluster, DynamicWebAppCluster.SERVICE_UP, false);
    }
    
    @Test
    public void testPropertiesToChildren() throws Exception {
        DynamicWebAppCluster cluster = app.createAndManageChild(EntitySpec.create(DynamicWebAppCluster.class)
            .configure("factory", new BasicConfigurableEntityFactory<TestJavaWebAppEntity>(MutableMap.of("a", 1), TestJavaWebAppEntity.class))
            .configure(DynamicWebAppCluster.CUSTOM_CHILD_FLAGS, ImmutableMap.of("b", 2)));

        app.start(ImmutableList.of(new SimulatedLocation()));
        
        TestJavaWebAppEntity we = (TestJavaWebAppEntity) Iterables.getOnlyElement(cluster.getMembers());
        assertEquals(we.a, 1);
        assertEquals(we.b, 2);
    }
}
