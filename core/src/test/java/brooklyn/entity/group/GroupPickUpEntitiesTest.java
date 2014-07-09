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
package brooklyn.entity.group;

import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.BrooklynAppUnitTestSupport;
import brooklyn.entity.Entity;
import brooklyn.entity.Group;
import brooklyn.entity.basic.BasicGroup;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.trait.Startable;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.policy.PolicySpec;
import brooklyn.policy.basic.AbstractPolicy;
import brooklyn.test.Asserts;
import brooklyn.test.EntityTestUtils;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.javalang.Boxing;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;

/**
 * tests that a group's membership gets updated using subscriptions
 */
public class GroupPickUpEntitiesTest extends BrooklynAppUnitTestSupport {

    private BasicGroup group;

    @BeforeMethod(alwaysRun = true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        group = app.createAndManageChild(EntitySpec.create(BasicGroup.class));
        
        group.addPolicy(PolicySpec.create(FindUpServicesWithNameBob.class));
    }

    @Test
    public void testGroupFindsElement() {
        Assert.assertEquals(group.getMembers().size(), 0);
        EntityTestUtils.assertAttributeEquals(group, BasicGroup.GROUP_SIZE, 0);
        
        TestEntity e1 = app.createAndManageChild(EntitySpec.create(TestEntity.class));

        EntityTestUtils.assertAttributeEquals(group, BasicGroup.GROUP_SIZE, 0);

        e1.setAttribute(Startable.SERVICE_UP, true);
        e1.setAttribute(TestEntity.NAME, "bob");

        EntityTestUtils.assertAttributeEqualsEventually(group, BasicGroup.GROUP_SIZE, 1);
        Asserts.assertEqualsIgnoringOrder(group.getAttribute(BasicGroup.GROUP_MEMBERS), ImmutableList.of(e1));
        
        TestEntity e2 = app.createAndManageChild(EntitySpec.create(TestEntity.class));

        EntityTestUtils.assertAttributeEquals(group, BasicGroup.GROUP_SIZE, 1);
        Assert.assertEquals(group.getMembers().size(), 1);
        Assert.assertTrue(group.getMembers().contains(e1));

        e2.setAttribute(Startable.SERVICE_UP, true);
        e2.setAttribute(TestEntity.NAME, "fred");

        EntityTestUtils.assertAttributeEquals(group, BasicGroup.GROUP_SIZE, 1);

        e2.setAttribute(TestEntity.NAME, "BOB");
        EntityTestUtils.assertAttributeEqualsEventually(group, BasicGroup.GROUP_SIZE, 2);
        Asserts.assertEqualsIgnoringOrder(group.getAttribute(BasicGroup.GROUP_MEMBERS), ImmutableList.of(e1, e2));
    }


    /**
     * sets the membership of a group to be all up services;
     * callers can subclass and override {@link #checkMembership(Entity)} to add additional membership constraints,
     * and optionally {@link #init()} to apply additional subscriptions
     */
    public static class FindUpServices extends AbstractPolicy {

        @SuppressWarnings({"rawtypes"})
        protected final SensorEventListener handler = new SensorEventListener() {
            @Override
            public void onEvent(SensorEvent event) {
                updateMembership(event.getSource());
            }
        };

        @Override
        public void setEntity(EntityLocal entity) {
            assert entity instanceof Group;
            super.setEntity(entity);
            subscribe(null, Startable.SERVICE_UP, handler);
            for (Entity e : ((EntityInternal) entity).getManagementContext().getEntityManager().getEntities()) {
                if (Objects.equal(e.getApplicationId(), entity.getApplicationId()))
                    updateMembership(e);
            }
        }

        protected Group getGroup() {
            return (Group) entity;
        }

        protected void updateMembership(Entity e) {
            boolean isMember = checkMembership(e);
            if (isMember) getGroup().addMember(e);
            else getGroup().removeMember(e);
        }

        protected boolean checkMembership(Entity e) {
            if (!Entities.isManaged(e)) return false;
            if (!Boxing.unboxSafely(e.getAttribute(Startable.SERVICE_UP), false)) return false;
            return true;
        }
    }


    public static class FindUpServicesWithNameBob extends FindUpServices {

        @Override
        public void setEntity(EntityLocal entity) {
            super.setEntity(entity);
            subscribe(null, TestEntity.NAME, handler);
        }

        @Override
        protected boolean checkMembership(Entity e) {
            if (!super.checkMembership(e)) return false;
            if (!"Bob".equalsIgnoreCase(e.getAttribute(TestEntity.NAME))) return false;
            return true;
        }
    }

}
