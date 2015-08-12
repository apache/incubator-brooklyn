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
import static org.testng.Assert.fail;

import org.apache.brooklyn.test.entity.TestEntity;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.BrooklynAppUnitTestSupport;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.location.Location;
import brooklyn.location.MachineProvisioningLocation;
import brooklyn.location.basic.SshMachineLocation;

import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class EntitySuppliersTest extends BrooklynAppUnitTestSupport {

    private TestEntity entity;
    private Location loc;
    private SshMachineLocation machine;
    
    @BeforeMethod(alwaysRun=true)
    @SuppressWarnings("unchecked")
    @Override
    public void setUp() throws Exception {
        super.setUp();
        entity = app.createAndManageChild(EntitySpec.create(TestEntity.class).displayName("mydisplayname"));
        loc = app.getManagementContext().getLocationRegistry().resolve("localhost");
        machine = ((MachineProvisioningLocation<SshMachineLocation>)loc).obtain(ImmutableMap.of());
    }

    @Test
    public void testUniqueSshMachineLocation() throws Exception {
        entity.addLocations(ImmutableList.of(machine));
        assertEquals(EntitySuppliers.uniqueSshMachineLocation(entity).get(), machine);
    }
    
    @Test
    public void testUniqueSshMachineLocationWhenNoLocation() throws Exception {
        Supplier<SshMachineLocation> supplier = EntitySuppliers.uniqueSshMachineLocation(entity);
        try {
            supplier.get();
            fail();
        } catch (IllegalStateException e) {
            // expected: success
        }
    }
}
