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

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.BrooklynAppUnitTestSupport;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.location.LocationSpec;
import brooklyn.location.basic.SimulatedLocation;
import brooklyn.test.entity.TestEntity;

import com.google.common.collect.ImmutableList;


public class QuarantineGroupTest extends BrooklynAppUnitTestSupport {

    private static final int TIMEOUT_MS = 2000;

    SimulatedLocation loc;
    private TestEntity e1;
    private TestEntity e2;
    private QuarantineGroup group;

    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        mgmt.getLocationManager().createLocation(LocationSpec.create(SimulatedLocation.class));
        e1 = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        e2 = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        group = app.createAndManageChild(EntitySpec.create(QuarantineGroup.class));
    }

    @Test
    public void testExpungeMembersWhenNone() throws Exception {
        group.expungeMembers(true);
        group.expungeMembers(false);
    }
    
    @Test
    public void testExpungeMembersWithoutStop() throws Exception {
        group.addMember(e1);
        group.addMember(e2);
        group.expungeMembers(false);
        
        assertFalse(Entities.isManaged(e1));
        assertFalse(Entities.isManaged(e2));
        assertEquals(e1.getCallHistory(), ImmutableList.of());
        assertEquals(e2.getCallHistory(), ImmutableList.of());
    }

    @Test
    public void testExpungeMembersWithStop() throws Exception {
        group.addMember(e1);
        group.addMember(e2);
        group.expungeMembers(true);
        
        assertFalse(Entities.isManaged(e1));
        assertFalse(Entities.isManaged(e2));
        assertEquals(e1.getCallHistory(), ImmutableList.of("stop"));
        assertEquals(e2.getCallHistory(), ImmutableList.of("stop"));
    }
}
