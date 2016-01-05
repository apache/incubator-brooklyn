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
package org.apache.brooklyn.entity.stock;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.location.LocationSpec;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.RecordingSensorEventListener;
import org.apache.brooklyn.core.entity.factory.ApplicationBuilder;
import org.apache.brooklyn.core.entity.lifecycle.Lifecycle;
import org.apache.brooklyn.core.location.SimulatedLocation;
import org.apache.brooklyn.core.location.Locations.LocationsFilter;
import org.apache.brooklyn.core.test.entity.LocalManagementContextForTests;
import org.apache.brooklyn.core.test.entity.TestApplication;
import org.apache.brooklyn.core.test.entity.TestEntity;
import org.apache.brooklyn.entity.stock.BasicEntity;
import org.apache.brooklyn.entity.stock.BasicStartable;
import org.apache.brooklyn.util.collections.MutableSet;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

public class BasicStartableTest {

    private ManagementContext managementContext;
    private SimulatedLocation loc1;
    private SimulatedLocation loc2;
    private TestApplication app;
    private BasicStartable startable;
    private TestEntity entity;
    private TestEntity entity2;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        managementContext = LocalManagementContextForTests.newInstance();
        loc1 = managementContext.getLocationManager().createLocation(LocationSpec.create(SimulatedLocation.class));
        loc2 = managementContext.getLocationManager().createLocation(LocationSpec.create(SimulatedLocation.class));
        app = ApplicationBuilder.newManagedApp(TestApplication.class, managementContext);
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (managementContext != null) Entities.destroyAll(managementContext);
    }
    
    @Test
    public void testSetsLocations() throws Exception {
        startable = app.addChild(EntitySpec.create(BasicStartable.class));
        app.start(ImmutableList.of(loc1, loc2));
        
        assertEqualsIgnoringOrder(startable.getLocations(), ImmutableSet.of(loc1, loc2));
    }
    
    @Test
    public void testDefaultIsAllLocations() throws Exception {
        startable = app.addChild(EntitySpec.create(BasicStartable.class));
        entity = startable.addChild(EntitySpec.create(TestEntity.class));
        entity2 = startable.addChild(EntitySpec.create(TestEntity.class));
        app.start(ImmutableList.of(loc1, loc2));
        
        assertEqualsIgnoringOrder(entity.getLocations(), ImmutableSet.of(loc1, loc2));
        assertEqualsIgnoringOrder(entity2.getLocations(), ImmutableSet.of(loc1, loc2));
    }
    
    @Test
    public void testAppliesFilterToEntities() throws Exception {
        final List<Object> contexts = Lists.newCopyOnWriteArrayList();
        
        LocationsFilter filter = new LocationsFilter() {
            @Override public List<Location> filterForContext(List<Location> locations, Object context) {
                contexts.add(context);
                assertEquals(locations, ImmutableList.of(loc1, loc2));
                if (context instanceof Entity) {
                    String entityName = ((Entity)context).getDisplayName();
                    if ("1".equals(entityName)) {
                        return ImmutableList.<Location>of(loc1);
                    } else if ("2".equals(entityName)) {
                        return ImmutableList.<Location>of(loc2);
                    } else {
                        return ImmutableList.<Location>of();
                    }
                } else {
                    return ImmutableList.<Location>of();
                }
            }
        };
        startable = app.addChild(EntitySpec.create(BasicStartable.class)
                .configure(BasicStartable.LOCATIONS_FILTER, filter));
        entity = startable.addChild(EntitySpec.create(TestEntity.class).displayName("1"));
        entity2 = startable.addChild(EntitySpec.create(TestEntity.class).displayName("2"));
        app.start(ImmutableList.of(loc1, loc2));
        
        assertEqualsIgnoringOrder(entity.getLocations(), ImmutableSet.of(loc1));
        assertEqualsIgnoringOrder(entity2.getLocations(), ImmutableSet.of(loc2));
        assertEqualsIgnoringOrder(contexts, ImmutableList.of(entity, entity2));
    }
    
    @Test
    public void testIgnoresUnstartableEntities() throws Exception {
        final AtomicReference<Exception> called = new AtomicReference<Exception>();
        LocationsFilter filter = new LocationsFilter() {
            @Override public List<Location> filterForContext(List<Location> locations, Object context) {
                called.set(new Exception());
                return locations;
            }
        };
        startable = app.addChild(EntitySpec.create(BasicStartable.class)
                .configure(BasicStartable.LOCATIONS_FILTER, filter));
        BasicEntity entity = startable.addChild(EntitySpec.create(BasicEntity.class));
        app.start(ImmutableList.of(loc1, loc2));
        
        assertEqualsIgnoringOrder(entity.getLocations(), ImmutableSet.of());
        assertNull(called.get());
    }

    @Test
    public void testTransitionsThroughLifecycles() throws Exception {
        startable = app.addChild(EntitySpec.create(BasicStartable.class));
        RecordingSensorEventListener<Lifecycle> listener = new RecordingSensorEventListener<Lifecycle>(true);
        managementContext.getSubscriptionContext(startable)
                .subscribe(startable, Attributes.SERVICE_STATE_ACTUAL, listener);

        app.start(ImmutableList.of(loc1));
        app.stop();

        ArrayList<Lifecycle> expected = Lists.newArrayList(
                Lifecycle.STARTING,
                Lifecycle.RUNNING,
                Lifecycle.STOPPING,
                Lifecycle.STOPPED);
        Iterable<Lifecycle> actual = listener.getEventValuesSortedByTimestamp();
        assertEquals(actual, expected,
                "Expected=" + Iterables.toString(expected) + ", actual=" + Iterables.toString(actual));
    }
    
    private void assertEqualsIgnoringOrder(Iterable<? extends Object> col1, Iterable<? extends Object> col2) {
        assertEquals(Iterables.size(col1), Iterables.size(col2), "col2="+col1+"; col2="+col2);
        assertEquals(MutableSet.copyOf(col1), MutableSet.copyOf(col2), "col1="+col1+"; col2="+col2);
    }
}
