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

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntityLocal;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.trait.Startable;
import org.apache.brooklyn.core.test.entity.TestApplication;
import org.apache.brooklyn.test.EntityTestUtils;
import org.apache.brooklyn.test.entity.TestJavaWebAppEntity;
import org.apache.brooklyn.util.collections.MutableMap;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.apache.brooklyn.location.core.SimulatedLocation;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;

public class DynamicWebAppClusterTest {
    
    private static final int SHORT_WAIT_MS = 250;
    
    private TestApplication app;
    private SimulatedLocation loc;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        app = TestApplication.Factory.newManagedInstanceForTests();
        loc = app.newSimulatedLocation();
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }

    @Test
    public void testTestJavaWebAppEntityStarts() throws Exception {
        Entity test = app.createAndManageChild(EntitySpec.create(TestJavaWebAppEntity.class));
        test.invoke(Startable.START, ImmutableMap.of("locations", ImmutableList.of(loc))).get();
        
        EntityTestUtils.assertAttributeEqualsEventually(test, Attributes.SERVICE_UP, true);
    }
    
    @Test
    public void testRequestCountAggregation() throws Exception {
        final DynamicWebAppCluster cluster = app.createAndManageChild(EntitySpec.create(DynamicWebAppCluster.class)
                .configure("initialSize", 2)
                .configure(ControlledDynamicWebAppCluster.MEMBER_SPEC, EntitySpec.create(TestJavaWebAppEntity.class)) );
        
        app.start(ImmutableList.of(loc));
        
        for (Entity member : cluster.getMembers()) {
            ((TestJavaWebAppEntity)member).spoofRequest();
        }
        EntityTestUtils.assertAttributeEqualsEventually(cluster, DynamicWebAppCluster.REQUEST_COUNT, 2);
        
        for (Entity member : cluster.getMembers()) {
            for (int i = 0; i < 2; i++) {
                ((TestJavaWebAppEntity)member).spoofRequest();
            }
        }
        EntityTestUtils.assertAttributeEqualsEventually(cluster, DynamicWebAppCluster.REQUEST_COUNT_PER_NODE, 3d);
    }
    
    @Test
    public void testSetsServiceUpIfMemberIsUp() throws Exception {
        DynamicWebAppCluster cluster = app.createAndManageChild(EntitySpec.create(DynamicWebAppCluster.class)
                .configure("initialSize", 1)
                .configure(ControlledDynamicWebAppCluster.MEMBER_SPEC, EntitySpec.create(TestJavaWebAppEntity.class)) );
    
        app.start(ImmutableList.of(loc));
        
        // Should initially be true (now that TestJavaWebAppEntity sets true) 
        EntityTestUtils.assertAttributeEqualsEventually(cluster, DynamicWebAppCluster.SERVICE_UP, true);
        
        // When child is !service_up, should report false
        ((EntityLocal)Iterables.get(cluster.getMembers(), 0)).setAttribute(Startable.SERVICE_UP, false);
        EntityTestUtils.assertAttributeEqualsEventually(cluster, DynamicWebAppCluster.SERVICE_UP, false);
        EntityTestUtils.assertAttributeEqualsContinually(MutableMap.of("timeout", SHORT_WAIT_MS), cluster, DynamicWebAppCluster.SERVICE_UP, false);
        
        cluster.resize(2);
        
        // When one of the two children is service_up, should report true
        EntityTestUtils.assertAttributeEqualsEventually(cluster, DynamicWebAppCluster.SERVICE_UP, true);

        // And if that serviceUp child goes away, should again report false
        Entities.unmanage(Iterables.get(cluster.getMembers(), 1));
        ((EntityLocal)Iterables.get(cluster.getMembers(), 0)).setAttribute(Startable.SERVICE_UP, false);
        
        EntityTestUtils.assertAttributeEqualsEventually(cluster, DynamicWebAppCluster.SERVICE_UP, false);
    }
    
    @Test
    public void testPropertiesToChildren() throws Exception {
        DynamicWebAppCluster cluster = app.createAndManageChild(EntitySpec.create(DynamicWebAppCluster.class)
            .configure(ControlledDynamicWebAppCluster.MEMBER_SPEC, EntitySpec.create(TestJavaWebAppEntity.class)
                .configure("a", 1))
            .configure(DynamicWebAppCluster.CUSTOM_CHILD_FLAGS, ImmutableMap.of("b", 2)));

        app.start(ImmutableList.of(loc));
        
        TestJavaWebAppEntity we = (TestJavaWebAppEntity) Iterables.getOnlyElement(cluster.getMembers());
        assertEquals(we.getA(), 1);
        assertEquals(we.getB(), 2);
    }
}
