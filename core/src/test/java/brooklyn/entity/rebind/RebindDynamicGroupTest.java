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

import java.util.Collection;

import org.testng.annotations.Test;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.DynamicGroup;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.rebind.RebindEntityTest.MyEntity;
import brooklyn.test.Asserts;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;

public class RebindDynamicGroupTest extends RebindTestFixtureWithApp {

    @Test
    public void testRestoresDynamicGroup() throws Exception {
        origApp.createAndManageChild(EntitySpec.create(MyEntity.class));
        origApp.createAndManageChild(EntitySpec.create(DynamicGroup.class)
                .configure(DynamicGroup.ENTITY_FILTER, Predicates.instanceOf(MyEntity.class)));
        
        newApp = rebind();
        final DynamicGroup newG = (DynamicGroup) Iterables.find(newApp.getChildren(), Predicates.instanceOf(DynamicGroup.class));
        final MyEntity newE = (MyEntity) Iterables.find(newApp.getChildren(), Predicates.instanceOf(MyEntity.class));

        // Rebound group should contain same members as last time
        assertGroupMemebers(newG, ImmutableSet.of(newE));

        // And should detect new members that match the filter
        final MyEntity newE2 = newApp.createAndManageChild(EntitySpec.create(MyEntity.class));
        Entities.manage(newE2);
        
        Asserts.succeedsEventually(new Runnable() {
            @Override public void run() {
                assertGroupMemebers(newG, ImmutableSet.of(newE, newE2));
            }});
    }

    private void assertGroupMemebers(DynamicGroup group, Collection<? extends Entity> expected) {
        assertEquals(Sets.newHashSet(group.getMembers()), ImmutableSet.copyOf(expected));
        assertEquals(group.getMembers().size(), expected.size(), "members="+group.getMembers());
    }
    
}
