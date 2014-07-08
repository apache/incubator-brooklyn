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
package brooklyn.entity.basic;

import static org.testng.Assert.assertEquals;

import java.util.List;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.BrooklynAppUnitTestSupport;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.location.basic.SimulatedLocation;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/**
 * Tests the deprecated use of AbstractAppliation, where its constructor is called directly.
 * 
 * @author aled
 */
public class AbstractApplicationLegacyTest extends BrooklynAppUnitTestSupport {

    private List<SimulatedLocation> locs;
    
    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        locs = ImmutableList.of(new SimulatedLocation());
    }
    
    // App and its children will be implicitly managed on first effector call on app
    @Test
    public void testStartAndStopCallsChildren() throws Exception {
        // deliberately unmanaged
        TestApplication app2 = mgmt.getEntityManager().createEntity(EntitySpec.create(TestApplication.class));
        TestEntity child = app2.addChild(EntitySpec.create(TestEntity.class));
        
        app2.invoke(AbstractApplication.START, ImmutableMap.of("locations", locs)).get();
        assertEquals(child.getCount(), 1);
        assertEquals(app.getManagementContext().getEntityManager().getEntity(app.getId()), app);
        assertEquals(app.getManagementContext().getEntityManager().getEntity(child.getId()), child);
        
        app2.stop();
        assertEquals(child.getCount(), 0);
    }
    
    @Test
    public void testStartAndStopWhenManagedCallsChildren() {
        TestEntity child = app.createAndManageChild(EntitySpec.create(TestEntity.class));

        assertEquals(app.getManagementContext().getEntityManager().getEntity(app.getId()), app);
        assertEquals(app.getManagementContext().getEntityManager().getEntity(child.getId()), child);

        app.start(locs);
        assertEquals(child.getCount(), 1);
        
        app.stop();
        assertEquals(child.getCount(), 0);
    }
    
    @Test
    public void testStartDoesNotStartPremanagedChildren() {
        TestEntity child = app.addChild(EntitySpec.create(TestEntity.class));
        
        app.start(locs);
        assertEquals(child.getCount(), 0);
    }
    
    @Test
    public void testStartDoesNotStartUnmanagedChildren() {
        TestEntity child = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        Entities.unmanage(child);
        
        app.start(locs);
        assertEquals(child.getCount(), 0);
    }
    
    @Test
    public void testStopDoesNotStopUnmanagedChildren() {
        TestEntity child = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        
        app.start(locs);
        assertEquals(child.getCount(), 1);
        
        Entities.unmanage(child);
        
        app.stop();
        assertEquals(child.getCount(), 1);
    }
    
    @Test
    public void testStopDoesNotStopPremanagedChildren() {
        app.start(locs);
        
        TestEntity child = app.addChild(EntitySpec.create(TestEntity.class));
        
        app.stop();
        assertEquals(child.getCount(), 0);
    }
}
