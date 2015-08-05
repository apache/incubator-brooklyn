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

import java.util.Arrays;
import java.util.List;

import org.apache.brooklyn.entity.basic.RecordingSensorEventListener;
import org.testng.Assert;
import org.testng.annotations.Test;

import brooklyn.entity.BrooklynAppUnitTestSupport;
import brooklyn.event.SensorEvent;
import brooklyn.location.Location;
import brooklyn.test.Asserts;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

public class EntityLocationsTest extends BrooklynAppUnitTestSupport {

    @Test
    public void testDuplicateLocationOnlyAddedOnce() {
        Location l = app.newSimulatedLocation();
        app.addLocations(Arrays.asList(l, l));
        app.addLocations(Arrays.asList(l, l));
        Assert.assertEquals(app.getLocations().size(), 1);
    }
    
    @Test
    public void testNotifiedOfAddAndRemoveLocations() throws Exception {
        final Location l = app.newSimulatedLocation();
        final Location l2 = app.newSimulatedLocation();
        
        final RecordingSensorEventListener<Object> addedEvents = new RecordingSensorEventListener<>();
        final RecordingSensorEventListener<Object> removedEvents = new RecordingSensorEventListener<>();
        app.subscribe(app, AbstractEntity.LOCATION_ADDED, addedEvents);
        app.subscribe(app, AbstractEntity.LOCATION_REMOVED, removedEvents);

        // Add first location
        app.addLocations(ImmutableList.of(l));
        
        assertEventValuesEqualsEventually(addedEvents, ImmutableList.of(l));
        assertEventValuesEquals(removedEvents, ImmutableList.of());

        // Add second location
        app.addLocations(ImmutableList.of(l2));

        assertEventValuesEqualsEventually(addedEvents, ImmutableList.of(l, l2));
        assertEventValuesEquals(removedEvents, ImmutableList.of());

        // Remove first location
        app.removeLocations(ImmutableList.of(l));
        
        assertEventValuesEqualsEventually(removedEvents, ImmutableList.of(l));
        assertEventValuesEquals(addedEvents, ImmutableList.of(l, l2));

        // Remove second location
        app.removeLocations(ImmutableList.of(l2));
        
        assertEventValuesEqualsEventually(removedEvents, ImmutableList.of(l, l2));
        assertEventValuesEquals(addedEvents, ImmutableList.of(l, l2));
    }
    
    @Test(groups="Integration") // because takes a second
    public void testNotNotifiedDuplicateAddedLocations() throws Exception {
        final Location l = app.newSimulatedLocation();
        
        final RecordingSensorEventListener<Object> addedEvents = new RecordingSensorEventListener<>();
        final RecordingSensorEventListener<Object> removedEvents = new RecordingSensorEventListener<>();
        app.subscribe(app, AbstractEntity.LOCATION_ADDED, addedEvents);
        app.subscribe(app, AbstractEntity.LOCATION_REMOVED, removedEvents);

        // Add first location
        app.addLocations(ImmutableList.of(l, l));
        
        assertEventValuesEqualsEventually(addedEvents, ImmutableList.of(l));
        assertEventValuesEquals(removedEvents, ImmutableList.of());

        // Add second location
        app.addLocations(ImmutableList.of(l));

        assertEventValuesEqualsContinually(addedEvents, ImmutableList.of(l));
        assertEventValuesEquals(removedEvents, ImmutableList.of());
    }
    
    private void assertEventValuesEqualsEventually(final RecordingSensorEventListener<Object> listener, final List<?> expectedVals) {
        Asserts.succeedsEventually(new Runnable() {
            @Override public void run() {
                assertEventValuesEquals(listener, expectedVals);
            }});
    }
    
    private void assertEventValuesEqualsContinually(final RecordingSensorEventListener<Object> listener, final List<?> expectedVals) {
        Asserts.succeedsContinually(new Runnable() {
            @Override public void run() {
                assertEventValuesEquals(listener, expectedVals);
            }});
    }

    private void assertEventValuesEquals(final RecordingSensorEventListener<Object> listener, final List<?> expectedVals) {
        Iterable<SensorEvent<Object>> events = listener.getEvents();
        assertEquals(Iterables.size(events), expectedVals.size(), "events=" + events);
        for (int i = 0; i < expectedVals.size(); i++) {
            Object expectedVal = expectedVals.get(i);
            assertEquals(Iterables.get(events, i).getValue(), expectedVal, "events=" + events);
        }
    }
}
