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
package brooklyn.entity.rebind;

import static org.testng.Assert.assertEquals;

import java.util.List;

import org.testng.annotations.Test;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractGroup;
import brooklyn.entity.basic.AbstractGroupImpl;
import brooklyn.entity.basic.BasicGroup;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.test.Asserts;
import brooklyn.test.entity.TestEntity;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

public class RebindGroupTest extends RebindTestFixtureWithApp {

    @Test
    public void testRestoresGroupAndMembers() throws Exception {
        BasicGroup origGroup = origApp.createAndManageChild(EntitySpec.create(BasicGroup.class));
        TestEntity origEntity = origApp.createAndManageChild(EntitySpec.create(TestEntity.class));
        origGroup.addMember(origEntity);
        
        newApp = rebind();
        BasicGroup newGroup = (BasicGroup) Iterables.find(origApp.getChildren(), Predicates.instanceOf(BasicGroup.class));
        TestEntity newEntity = (TestEntity) Iterables.find(origApp.getChildren(), Predicates.instanceOf(TestEntity.class));
        Asserts.assertEqualsIgnoringOrder(newGroup.getMembers(), ImmutableSet.of(newEntity));
        assertEquals(newGroup.getAttribute(BasicGroup.GROUP_SIZE), (Integer)1);
        assertEquals(newGroup.getAttribute(BasicGroup.GROUP_MEMBERS), ImmutableSet.of(newEntity));
    }
    
    // FIXME Fails because attribute AbstractGroup.GROUP_MEMBERS is an ImmutableSet which cannot have null values.
    // However, deserializing the origEntity was a dangling reference which was returned as null.
    // Therefore deserializing the group fails.
    @Test(enabled=false)
    public void testRestoresGroupWithUnmanagedMember() throws Exception {
        BasicGroup origGroup = origApp.createAndManageChild(EntitySpec.create(BasicGroup.class));
        TestEntity origEntity = origApp.createAndManageChild(EntitySpec.create(TestEntity.class));
        Entities.unmanage(origEntity);
        origGroup.addMember(origEntity);
        
        newApp = rebind();
        BasicGroup newGroup = (BasicGroup) Iterables.find(origApp.getChildren(), Predicates.instanceOf(BasicGroup.class));
        Asserts.assertEqualsIgnoringOrder(newGroup.getMembers(), ImmutableSet.of());
        assertEquals(newGroup.getAttribute(BasicGroup.GROUP_SIZE), (Integer)0);
        assertEquals(newGroup.getAttribute(BasicGroup.GROUP_MEMBERS), ImmutableSet.of());
    }
    
    // Don't want rebind to call addMember, because some sub-classes override this; calling it kicks off that
    // behaviour again which is (often?) not what is wanted.
    // FIXME fails because BasicEntityRebindSupport.addMembers calls group.addMember
    @Test(enabled=false)
    public void testAddMemberNotCalledOnRebind() throws Exception {
        GroupRecordingCalls origGroup = origApp.createAndManageChild(EntitySpec.create(GroupRecordingCalls.class));
        TestEntity origEntity = origApp.createAndManageChild(EntitySpec.create(TestEntity.class));
        origGroup.addMember(origEntity);
        origGroup.clearCalls();
        
        newApp = rebind();
        GroupRecordingCalls newGroup = (GroupRecordingCalls) Iterables.find(newApp.getChildren(), Predicates.instanceOf(GroupRecordingCalls.class));
        TestEntity newEntity = (TestEntity) Iterables.find(origApp.getChildren(), Predicates.instanceOf(TestEntity.class));
        assertEquals(newGroup.getCalls(), ImmutableList.of());
        Asserts.assertEqualsIgnoringOrder(newGroup.getMembers(), ImmutableSet.of(newEntity));
    }
    
    @ImplementedBy(GroupRecordingCallsImpl.class)
    public static interface GroupRecordingCalls extends AbstractGroup {
        List<String> getCalls();
        void clearCalls();
    }
    
    public static class GroupRecordingCallsImpl extends AbstractGroupImpl implements GroupRecordingCalls {
        private final List<String> calls = Lists.newCopyOnWriteArrayList();
        
        @Override
        public List<String> getCalls() {
            return calls;
        }
        
        @Override
        public void clearCalls() {
            calls.clear();
        }
        
        @Override
        public boolean addMember(Entity member) {
            calls.add("addMember");
            return super.addMember(member);
        }
        
        @Override
        public boolean removeMember(Entity member) {
            calls.add("removeMember");
            return super.removeMember(member);
        }
    }
}
