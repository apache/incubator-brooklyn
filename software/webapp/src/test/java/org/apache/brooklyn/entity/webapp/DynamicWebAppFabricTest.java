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

import java.util.List;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.internal.EntityLocal;
import org.apache.brooklyn.core.test.entity.TestApplication;
import org.apache.brooklyn.entity.core.Entities;
import org.apache.brooklyn.entity.core.EntityInternal;
import org.apache.brooklyn.entity.group.DynamicGroup;
import org.apache.brooklyn.entity.trait.Changeable;
import org.apache.brooklyn.test.EntityTestUtils;
import org.apache.brooklyn.test.entity.TestJavaWebAppEntity;
import org.apache.brooklyn.util.collections.MutableMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.apache.brooklyn.location.core.SimulatedLocation;

import com.google.common.collect.ImmutableList;

/**
 * TODO clarify test purpose
 */
public class DynamicWebAppFabricTest {
    private static final Logger log = LoggerFactory.getLogger(DynamicWebAppFabricTest.class);

    private static final long TIMEOUT_MS = 10*1000;
    
    private TestApplication app;
    private SimulatedLocation loc1;
    private SimulatedLocation loc2;
    private List<SimulatedLocation> locs;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        app = TestApplication.Factory.newManagedInstanceForTests();
        loc1 = app.newSimulatedLocation();
        loc2 = app.newSimulatedLocation();
        locs = ImmutableList.of(loc1, loc2);
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }

    @Test
    public void testRequestCountAggregation() {
        DynamicWebAppFabric fabric = app.createAndManageChild(EntitySpec.create(DynamicWebAppFabric.class)
                .configure(DynamicWebAppFabric.MEMBER_SPEC, EntitySpec.create(TestJavaWebAppEntity.class)) );
        
        app.start(locs);
        for (Entity member : fabric.getChildren()) {
            ((EntityLocal)member).setAttribute(Changeable.GROUP_SIZE, 1);
        }
        
        for (Entity member : fabric.getChildren()) {
            ((EntityInternal)member).setAttribute(DynamicGroup.GROUP_SIZE, 1);
            ((TestJavaWebAppEntity)member).spoofRequest();
        }
        EntityTestUtils.assertAttributeEqualsEventually(MutableMap.of("timeout", TIMEOUT_MS), fabric, DynamicWebAppFabric.REQUEST_COUNT, 2);
        
        // Note this is time-sensitive: need to do the next two sends before the previous one has dropped out
        // of the time-window.
        for (Entity member : fabric.getChildren()) {
            for (int i = 0; i < 2; i++) {
                ((TestJavaWebAppEntity)member).spoofRequest();
            }
        }
        EntityTestUtils.assertAttributeEqualsEventually(MutableMap.of("timeout", TIMEOUT_MS), fabric, DynamicWebAppFabric.REQUEST_COUNT_PER_NODE, 3d);
    }
    
    @Test
    public void testRequestCountAggregationOverClusters() {
        DynamicWebAppFabric fabric = app.createAndManageChild(EntitySpec.create(DynamicWebAppFabric.class)
                .configure(DynamicWebAppFabric.MEMBER_SPEC, 
                    EntitySpec.create(DynamicWebAppCluster.class)
                        .configure("initialSize", 2)
                        .configure(ControlledDynamicWebAppCluster.MEMBER_SPEC, EntitySpec.create(TestJavaWebAppEntity.class)) ));

        app.start(locs);
        
        for (Entity cluster : fabric.getChildren()) {
            for (Entity node : ((DynamicWebAppCluster)cluster).getMembers()) {
                ((TestJavaWebAppEntity)node).spoofRequest();
            }
        }
        EntityTestUtils.assertAttributeEqualsEventually(MutableMap.of("timeout", TIMEOUT_MS), fabric, DynamicWebAppFabric.REQUEST_COUNT, 4);
        
        // Note this is time-sensitive: need to do the next two sends before the previous one has dropped out
        // of the time-window.
        for (Entity cluster : fabric.getChildren()) {
            for (Entity node : ((DynamicWebAppCluster)cluster).getMembers()) {
                for (int i = 0; i < 2; i++) {
                    ((TestJavaWebAppEntity)node).spoofRequest();
                }
            }
        }
        EntityTestUtils.assertAttributeEqualsEventually(MutableMap.of("timeout", TIMEOUT_MS), fabric, DynamicWebAppFabric.REQUEST_COUNT_PER_NODE, 3d);
    }
}
