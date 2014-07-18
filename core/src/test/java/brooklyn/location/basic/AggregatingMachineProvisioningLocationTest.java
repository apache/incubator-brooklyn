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
package brooklyn.location.basic;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

import java.util.Map;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.Entities;
import brooklyn.location.Location;
import brooklyn.location.LocationSpec;
import brooklyn.location.MachineProvisioningLocation;
import brooklyn.location.NoMachinesAvailableException;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation.LocalhostMachine;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.test.entity.LocalManagementContextForTests;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class AggregatingMachineProvisioningLocationTest {

    
    private LocalManagementContext managementContext;
    private AggregatingMachineProvisioningLocation<LocalhostMachine> aggregator;
    private LocalhostMachine machine1a;
    private LocalhostMachine machine1b;
    private LocalhostMachine machine2a;
    private LocalhostMachine machine2b;
    private MachineProvisioningLocation<LocalhostMachine> provisioner1;
    private MachineProvisioningLocation<LocalhostMachine> provisioner2;
    
    @BeforeMethod(alwaysRun=true)
    @SuppressWarnings("unchecked")
    public void setUp() {
        managementContext = new LocalManagementContextForTests();
        machine1a = newLocation(LocalhostMachine.class, "1a");
        machine1b = newLocation(LocalhostMachine.class, "1b");
        machine2a = newLocation(LocalhostMachine.class, "2a");
        machine2b = newLocation(LocalhostMachine.class, "2b");
        provisioner1 = newLocation(FixedListMachineProvisioningLocation.class, ImmutableMap.of("machines", ImmutableList.of(machine1a, machine1b)));
        provisioner2 = newLocation(FixedListMachineProvisioningLocation.class, ImmutableMap.of("machines", ImmutableList.of(machine2a, machine2b)));
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() {
        if (managementContext != null) Entities.destroyAll(managementContext);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testObtainAndRelease() throws Exception {
        aggregator = newLocation(AggregatingMachineProvisioningLocation.class, ImmutableMap.of("provisioners", ImmutableList.of(provisioner1, provisioner2)));
        assertEquals(aggregator.obtain(), machine1a);
        assertEquals(aggregator.obtain(), machine2a);
        assertEquals(aggregator.obtain(), machine1b);
        assertEquals(aggregator.obtain(), machine2b);
        
        try {
            aggregator.obtain();
            fail();
        } catch (NoMachinesAvailableException e) {
            // success
        }
        
        aggregator.release(machine2b);
        assertEquals(aggregator.obtain(), machine2b);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testReleaseWhenNotHeldThrows() throws Exception {
        aggregator = newLocation(AggregatingMachineProvisioningLocation.class, ImmutableMap.of("provisioners", ImmutableList.of(provisioner1, provisioner2)));
        try {
            aggregator.release(machine1a);
            fail();
        } catch (IllegalStateException e) {
            if (!e.toString().contains("machine is not currently allocated")) throw e;
        }
    }

    private <T extends Location> T newLocation(Class<T> clazz, String displayName) {
        return newLocation(clazz, displayName, ImmutableMap.of());
    }

    private <T extends Location> T newLocation(Class<T> clazz, Map<?,?> config) {
        return newLocation(clazz, "mydisplayname", config);
    }
    
    private <T extends Location> T newLocation(Class<T> clazz, String displayName, Map<?,?> config) {
        return managementContext.getLocationManager().createLocation(LocationSpec.create(clazz)
                .displayName(displayName)
                .configure(config));
    }
}
