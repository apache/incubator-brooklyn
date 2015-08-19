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
package org.apache.brooklyn.core.entity;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.util.List;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.LocationSpec;
import org.apache.brooklyn.core.entity.AbstractApplication;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.factory.ApplicationBuilder;
import org.apache.brooklyn.core.location.SimulatedLocation;
import org.apache.brooklyn.core.test.BrooklynAppUnitTestSupport;
import org.apache.brooklyn.core.test.entity.TestApplication;
import org.apache.brooklyn.core.test.entity.TestEntity;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/**
 * Tests the deprecated use of AbstractAppliation, where its constructor is called directly.
 * 
 * @author aled
 */
public class AbstractApplicationLegacyTest extends BrooklynAppUnitTestSupport {

    private SimulatedLocation loc;
    private List<SimulatedLocation> locs;
    
    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        loc = mgmt.getLocationManager().createLocation(LocationSpec.create(SimulatedLocation.class));
        locs = ImmutableList.of(loc);
    }
    
    // App and its children will be implicitly managed on first effector call on app
    @Test
    public void testStartAndStopUnmanagedAppAutomanagesTheAppAndChildren() throws Exception {
        // deliberately unmanaged
        TestApplication app2 = mgmt.getEntityManager().createEntity(EntitySpec.create(TestApplication.class));
        TestEntity child = app2.addChild(EntitySpec.create(TestEntity.class));
        assertFalse(Entities.isManaged(app2));
        assertFalse(Entities.isManaged(child));
        
        app2.invoke(AbstractApplication.START, ImmutableMap.of("locations", locs)).get();
        assertTrue(Entities.isManaged(app2));
        assertTrue(Entities.isManaged(child));
        assertEquals(child.getCallHistory(), ImmutableList.of("start"));
        assertEquals(mgmt.getEntityManager().getEntity(app2.getId()), app2);
        assertEquals(mgmt.getEntityManager().getEntity(child.getId()), child);
        
        app2.stop();
        assertEquals(child.getCallHistory(), ImmutableList.of("start", "stop"));
        assertFalse(Entities.isManaged(child));
        assertFalse(Entities.isManaged(app2));
    }
    
    @Test
    public void testStartAndStopWhenManagedCallsChildren() {
        TestEntity child = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        assertTrue(Entities.isManaged(app));
        assertTrue(Entities.isManaged(child));

        app.start(locs);
        assertEquals(child.getCallHistory(), ImmutableList.of("start"));
        
        app.stop();
        assertEquals(child.getCallHistory(), ImmutableList.of("start", "stop"));
        assertFalse(Entities.isManaged(child));
        assertFalse(Entities.isManaged(app));
    }
    
    @Test
    public void testStartOnManagedAppDoesNotStartPremanagedChildren() {
        TestEntity child = app.addChild(EntitySpec.create(TestEntity.class));
        
        app.start(locs);
        assertEquals(child.getCallHistory(), ImmutableList.of());
    }
    
    @Test
    public void testStartOnManagedAppDoesNotStartUnmanagedChildren() {
        TestEntity child = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        Entities.unmanage(child);
        
        app.start(locs);
        assertEquals(child.getCallHistory(), ImmutableList.of());
    }
    
    @Test
    public void testStopDoesNotStopUnmanagedChildren() {
        TestEntity child = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        
        app.start(locs);
        assertEquals(child.getCallHistory(), ImmutableList.of("start"));
        
        Entities.unmanage(child);
        
        app.stop();
        assertEquals(child.getCallHistory(), ImmutableList.of("start"));
    }
    
    @Test
    public void testStopOnManagedAppDoesNotStopPremanagedChildren() {
        app.start(locs);
        
        TestEntity child = app.addChild(EntitySpec.create(TestEntity.class));
        
        app.stop();
        assertEquals(child.getCallHistory(), ImmutableList.of());
    }
    
    @Test
    public void testAppUsesDefaultDisplayName() {
        EntitySpec<TestApplication> appSpec = EntitySpec.create(TestApplication.class)
                .configure(AbstractApplication.DEFAULT_DISPLAY_NAME, "myDefaultName");
        TestApplication app2 = ApplicationBuilder.newManagedApp(appSpec, mgmt);
        
        assertEquals(app2.getDisplayName(), "myDefaultName");
    }
    
    @Test
    public void testAppUsesDisplayNameOverDefaultName() {
        EntitySpec<TestApplication> appSpec = EntitySpec.create(TestApplication.class)
                .displayName("myName")
                .configure(AbstractApplication.DEFAULT_DISPLAY_NAME, "myDefaultName");
        TestApplication app2 = ApplicationBuilder.newManagedApp(appSpec, mgmt);
        
        assertEquals(app2.getDisplayName(), "myName");
    }
}
