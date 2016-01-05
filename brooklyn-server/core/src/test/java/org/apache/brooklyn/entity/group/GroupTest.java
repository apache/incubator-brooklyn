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
package org.apache.brooklyn.entity.group;

import static org.testng.Assert.assertEquals;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.entity.Group;
import org.apache.brooklyn.api.location.LocationSpec;
import org.apache.brooklyn.core.entity.AbstractEntity;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.RecordingSensorEventListener;
import org.apache.brooklyn.core.location.SimulatedLocation;
import org.apache.brooklyn.core.test.BrooklynAppUnitTestSupport;
import org.apache.brooklyn.core.test.entity.TestEntity;
import org.apache.brooklyn.entity.group.BasicGroup;
import org.apache.brooklyn.test.Asserts;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

public class GroupTest extends BrooklynAppUnitTestSupport {

    private BasicGroup group;
    private TestEntity entity1;
    private TestEntity entity2;
    
    SimulatedLocation loc;

    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        loc = app.getManagementContext().getLocationManager().createLocation(LocationSpec.create(SimulatedLocation.class));
        group = app.createAndManageChild(EntitySpec.create(BasicGroup.class));
        entity1 = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        entity2 = app.createAndManageChild(EntitySpec.create(TestEntity.class));
    }

    @Test
    public void testAddRemoveMembers() throws Exception {
        group.addMember(entity1);
        assertGroupMembers(entity1);
        
        group.addMember(entity2);
        assertGroupMembers(entity1, entity2);
        
        group.removeMember(entity2);
        assertGroupMembers(entity1);
        
        group.removeMember(entity1);
        assertGroupMembers(new Entity[0]);
    }
    
    @Test
    public void testEntityGetGroups() throws Exception {
        group.addMember(entity1);
        Asserts.assertEqualsIgnoringOrder(entity1.groups(), ImmutableSet.of(group));
        
        group.removeMember(entity1);
        Asserts.assertEqualsIgnoringOrder(entity1.groups(), ImmutableSet.of());
   }
    
    @Test
    public void testUnmanagedMemberAutomaticallyRemoved() throws Exception {
        group.addMember(entity1);
        Entities.unmanage(entity1);
        assertGroupMembers(new Entity[0]);
    }
    
    @Test
    public void testUnmanagedGroupAutomaticallyRemovedMembers() throws Exception {
        group.addMember(entity1);
        Entities.unmanage(group);
        Asserts.assertEqualsIgnoringOrder(entity1.groups(), ImmutableSet.of());
    }
    
    @Test
    public void testAddingUnmanagedMemberDoesNotFailBadly() throws Exception {
        Entities.unmanage(entity1);
        group.addMember(entity1);
        Entities.unmanage(group);
    }
    
    @Test
    public void testAddingUnmanagedGroupDoesNotFailBadly() throws Exception {
        Entities.unmanage(group);
        entity1.addGroup(group);
        Entities.unmanage(entity1);
    }
    
    @Test
    public void testAddingAndRemovingGroupEmitsNotification() throws Exception {
        final RecordingSensorEventListener<Group> groupAddedListener = new RecordingSensorEventListener<>();
        final RecordingSensorEventListener<Group> groupRemovedListener = new RecordingSensorEventListener<>();
        mgmt.getSubscriptionManager().subscribe(entity1, AbstractEntity.GROUP_ADDED, groupAddedListener);
        mgmt.getSubscriptionManager().subscribe(entity1, AbstractEntity.GROUP_REMOVED, groupRemovedListener);
        
        group.addMember(entity1);
        Asserts.succeedsEventually(new Runnable() {
            public void run() {
                String msg = "events="+groupAddedListener.getEvents();
                assertEquals(groupAddedListener.getEvents().size(), 1, msg);
                assertEquals(groupAddedListener.getEvents().get(0).getSource(), entity1, msg);
                assertEquals(groupAddedListener.getEvents().get(0).getSensor(), AbstractEntity.GROUP_ADDED, msg);
            }});
        assertEquals(groupRemovedListener.getEvents().size(), 0, "events="+groupRemovedListener.getEvents());
        
        group.removeMember(entity1);
        Asserts.succeedsEventually(new Runnable() {
            public void run() {
                String msg = "events="+groupRemovedListener.getEvents();
                assertEquals(groupRemovedListener.getEvents().size(), 1, msg);
                assertEquals(groupRemovedListener.getEvents().get(0).getSource(), entity1, msg);
                assertEquals(groupRemovedListener.getEvents().get(0).getSensor(), AbstractEntity.GROUP_REMOVED, msg);
            }});
        assertEquals(groupAddedListener.getEvents().size(), 1, "events="+groupAddedListener.getEvents());
    }
    
    private void assertGroupMembers(Entity... expectedMembers) {
        Asserts.assertEqualsIgnoringOrder(group.getMembers(), ImmutableList.copyOf(expectedMembers));
        assertEquals(group.getAttribute(BasicGroup.GROUP_SIZE), (Integer)expectedMembers.length);
    }
}
