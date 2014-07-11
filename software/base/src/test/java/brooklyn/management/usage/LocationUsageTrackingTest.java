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
package brooklyn.management.usage;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.BrooklynAppUnitTestSupport;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.entity.basic.SoftwareProcessEntityTest;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.location.LocationSpec;
import brooklyn.location.NoMachinesAvailableException;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.management.usage.LocationUsage.LocationEvent;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

public class LocationUsageTrackingTest extends BrooklynAppUnitTestSupport {

    private DynamicLocalhostMachineProvisioningLocation loc;
    
    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        loc = mgmt.getLocationManager().createLocation(LocationSpec.create(DynamicLocalhostMachineProvisioningLocation.class));
    }

    @Test
    public void testUsageInitiallyEmpty() {
        Set<LocationUsage> usage = mgmt.getUsageManager().getLocationUsage(Predicates.alwaysTrue());
        assertEquals(usage, ImmutableSet.of());
    }

    @Test
    public void testUsageIncludesStartAndStopEvents() {
        SoftwareProcessEntityTest.MyService entity = app.createAndManageChild(EntitySpec.create(SoftwareProcessEntityTest.MyService.class));
        
        // Start the app; expect record of location in use
        long preStart = System.currentTimeMillis();
        app.start(ImmutableList.of(loc));
        long postStart = System.currentTimeMillis();
        SshMachineLocation machine = Iterables.getOnlyElement(loc.getAllMachines());
        
        Set<LocationUsage> usages1 = mgmt.getUsageManager().getLocationUsage(Predicates.alwaysTrue());
        LocationUsage usage1 = Iterables.getOnlyElement(usages1);
        List<LocationEvent> events1 = usage1.getEvents();
        LocationEvent event1 = Iterables.getOnlyElement(events1);
        
        assertEquals(usage1.getLocationId(), machine.getId());
        assertEquals(event1.getApplicationId(), app.getId());
        assertEquals(event1.getEntityId(), entity.getId());
        assertEquals(event1.getState(), Lifecycle.CREATED);
        long event1Time = event1.getDate().getTime();
        assertTrue(event1Time >= preStart && event1Time <= postStart, "event1="+event1Time+"; pre="+preStart+"; post="+postStart);
        
        // Stop the app; expect record of location no longer in use
        long preStop = System.currentTimeMillis();
        app.stop();
        long postStop = System.currentTimeMillis();

        Set<LocationUsage> usages2 = mgmt.getUsageManager().getLocationUsage(Predicates.alwaysTrue());
        LocationUsage usage2 = Iterables.getOnlyElement(usages2);
        List<LocationEvent> events2 = usage2.getEvents();
        LocationEvent event2 = events2.get(1);

        assertEquals(events2.get(0).getDate(), event1.getDate());
        assertEquals(usage2.getLocationId(), machine.getId());
        assertEquals(event2.getApplicationId(), app.getId());
        assertEquals(event2.getEntityId(), entity.getId());
        assertEquals(event2.getState(), Lifecycle.DESTROYED);
        long event2Time = event2.getDate().getTime();
        assertTrue(event2Time >= preStop && event2Time <= postStop, "event2="+event2Time+"; pre="+preStop+"; post="+postStop);
    }
    
    public static class DynamicLocalhostMachineProvisioningLocation extends LocalhostMachineProvisioningLocation {
        @Override
        public SshMachineLocation obtain(Map<?, ?> flags) throws NoMachinesAvailableException {
            System.out.println("called DynamicLocalhostMachineProvisioningLocation.obtain");
            return super.obtain(flags);
        }
        
        @Override
        public void release(SshMachineLocation machine) {
            System.out.println("called DynamicLocalhostMachineProvisioningLocation.release");
            super.release(machine);
            super.machines.remove(machine);
            super.removeChild(machine);
        }
    }
}
