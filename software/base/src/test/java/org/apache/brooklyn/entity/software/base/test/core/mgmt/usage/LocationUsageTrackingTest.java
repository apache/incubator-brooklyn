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
package org.apache.brooklyn.entity.software.base.test.core.mgmt.usage;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.fail;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.location.LocationSpec;
import org.apache.brooklyn.api.location.NoMachinesAvailableException;
import org.apache.brooklyn.core.mgmt.internal.UsageListener.LocationMetadata;
import org.apache.brooklyn.core.mgmt.usage.LocationUsage;
import org.apache.brooklyn.core.mgmt.usage.LocationUsage.LocationEvent;
import org.apache.brooklyn.core.test.BrooklynAppUnitTestSupport;
import org.apache.brooklyn.entity.lifecycle.Lifecycle;
import org.apache.brooklyn.entity.software.base.SoftwareProcessEntityTest;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.apache.brooklyn.location.localhost.LocalhostMachineProvisioningLocation;
import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.apache.brooklyn.test.Asserts;
import org.apache.brooklyn.util.time.Time;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

public class LocationUsageTrackingTest extends BrooklynAppUnitTestSupport {

    private DynamicLocalhostMachineProvisioningLocation loc;

    @BeforeMethod(alwaysRun = true)
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
    @SuppressWarnings("deprecation")
    public void testAddAndRemoveLegacyUsageListener() throws Exception {
        final RecordingLegacyUsageListener listener = new RecordingLegacyUsageListener();
        mgmt.getUsageManager().addUsageListener(listener);
        
        app.createAndManageChild(EntitySpec.create(SoftwareProcessEntityTest.MyService.class));
        app.start(ImmutableList.of(loc));
        final SshMachineLocation machine = Iterables.getOnlyElement(loc.getAllMachines());
        
        Asserts.succeedsEventually(new Runnable() {
            @Override public void run() {
                List<List<?>> events = listener.getLocationEvents();
                String locId = (String) events.get(0).get(1);
                LocationEvent locEvent = (LocationEvent) events.get(0).get(3);
                Map<?,?> metadata = (Map<?, ?>) events.get(0).get(2);
                
                assertEquals(events.size(), 1, "events="+events);
                assertEquals(locId, machine.getId(), "events="+events);
                assertNotNull(metadata, "events="+events);
                assertEquals(locEvent.getApplicationId(), app.getId(), "events="+events);
                assertEquals(locEvent.getState(), Lifecycle.CREATED, "events="+events);
            }});

        // Remove the listener; will get no more notifications
        listener.clearEvents();
        mgmt.getUsageManager().removeUsageListener(listener);
        
        app.stop();
        Asserts.succeedsContinually(new Runnable() {
            @Override public void run() {
                List<List<?>> events = listener.getLocationEvents();
                assertEquals(events.size(), 0, "events="+events);
            }});
    }

    @Test
    public void testAddAndRemoveUsageListener() throws Exception {
        final RecordingUsageListener listener = new RecordingUsageListener();
        mgmt.getUsageManager().addUsageListener(listener);
        
        app.createAndManageChild(EntitySpec.create(SoftwareProcessEntityTest.MyService.class));
        app.start(ImmutableList.of(loc));
        final SshMachineLocation machine = Iterables.getOnlyElement(loc.getAllMachines());
        
        Asserts.succeedsEventually(new Runnable() {
            @Override public void run() {
                List<List<?>> events = listener.getLocationEvents();
                LocationMetadata locMetadata = (LocationMetadata) events.get(0).get(1);
                LocationEvent locEvent = (LocationEvent) events.get(0).get(2);
                
                assertEquals(events.size(), 1, "events="+events);
                assertEquals(locMetadata.getLocation(), machine, "events="+events);
                assertEquals(locMetadata.getLocationId(), machine.getId(), "events="+events);
                assertNotNull(locMetadata.getMetadata(), "events="+events);
                assertEquals(locEvent.getApplicationId(), app.getId(), "events="+events);
                assertEquals(locEvent.getState(), Lifecycle.CREATED, "events="+events);
            }});

        // Remove the listener; will get no more notifications
        listener.clearEvents();
        mgmt.getUsageManager().removeUsageListener(listener);
        
        app.stop();
        Asserts.succeedsContinually(new Runnable() {
            @Override public void run() {
                List<List<?>> events = listener.getLocationEvents();
                assertEquals(events.size(), 0, "events="+events);
            }});
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
        assertLocationUsage(usage1, machine);
        assertLocationEvent(usage1.getEvents().get(0), entity, Lifecycle.CREATED, preStart, postStart);

        // Stop the app; expect record of location no longer in use
        long preStop = System.currentTimeMillis();
        app.stop();
        long postStop = System.currentTimeMillis();

        Set<LocationUsage> usages2 = mgmt.getUsageManager().getLocationUsage(Predicates.alwaysTrue());
        LocationUsage usage2 = Iterables.getOnlyElement(usages2);
        assertLocationUsage(usage2, machine);
        assertLocationEvent(usage2.getEvents().get(1), app.getApplicationId(), entity.getId(), entity.getEntityType().getName(), Lifecycle.DESTROYED, preStop, postStop);
        
        assertEquals(usage2.getEvents().size(), 2, "usage="+usage2);
    }

    public static class DynamicLocalhostMachineProvisioningLocation extends LocalhostMachineProvisioningLocation {
        private static final long serialVersionUID = 4822009936654077946L;

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
    
    private void assertLocationUsage(LocationUsage usage, Location expectedLoc) {
        assertEquals(usage.getLocationId(), expectedLoc.getId(), "usage="+usage);
        assertNotNull(usage.getMetadata(), "usage="+usage);
    }

    private void assertLocationEvent(LocationEvent event, Entity expectedEntity, Lifecycle expectedState, long preEvent, long postEvent) {
        assertLocationEvent(event, expectedEntity.getApplicationId(), expectedEntity.getId(), expectedEntity.getEntityType().getName(), expectedState, preEvent, postEvent);
    }
    
    private void assertLocationEvent(LocationEvent event, String expectedAppId, String expectedEntityId, String expectedEntityType, Lifecycle expectedState, long preEvent, long postEvent) {
        // Saw times differ by 1ms - perhaps different threads calling currentTimeMillis() can get out-of-order times?!
        final int TIMING_GRACE = 5;
        
        assertEquals(event.getApplicationId(), expectedAppId);
        assertEquals(event.getEntityId(), expectedEntityId);
        assertEquals(event.getEntityType(), expectedEntityType);
        assertEquals(event.getState(), expectedState);
        long eventTime = event.getDate().getTime();
        if (eventTime < (preEvent - TIMING_GRACE) || eventTime > (postEvent + TIMING_GRACE)) {
            fail("for "+expectedState+": event=" + Time.makeDateString(eventTime) + "("+eventTime + "); "
                    + "pre=" + Time.makeDateString(preEvent) + " ("+preEvent+ "); "
                    + "post=" + Time.makeDateString(postEvent) + " ("+postEvent + ")");
        }
    }
}
