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

import java.util.Collection;
import java.util.List;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.BrooklynAppUnitTestSupport;
import brooklyn.entity.Entity;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.test.Asserts;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.collections.MutableMap;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;

public class EntityRegistrationTest extends BrooklynAppUnitTestSupport {

    private static final int TIMEOUT_MS = 10*1000;
    
    private TestEntity entity;
    private TestEntity entity2;

    private List<Entity> added;
    private List<Entity> removed;

    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        
        added = Lists.newCopyOnWriteArrayList();
        removed = Lists.newCopyOnWriteArrayList();
        
        app.subscribe(app, AbstractEntity.CHILD_ADDED, new SensorEventListener<Entity>() {
            @Override public void onEvent(SensorEvent<Entity> event) {
                added.add(event.getValue());
            }});
        app.subscribe(app, AbstractEntity.CHILD_REMOVED, new SensorEventListener<Entity>() {
                @Override public void onEvent(SensorEvent<Entity> event) {
                    removed.add(event.getValue());
                }});
    }
    
    @Test
    public void testAddAndRemoveChildrenEmitsEvent() {
        entity = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        assertCollectionEquals(app.getChildren(), ImmutableList.of(entity));
        assertEqualsEventually(added, ImmutableList.of(entity));
        
        entity2 = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        assertCollectionEquals(app.getChildren(), ImmutableList.of(entity, entity2));
        assertEqualsEventually(added, ImmutableList.of(entity, entity2));
        
        entity.removeChild(entity);
        assertCollectionEquals(app.getChildren(), ImmutableList.of(entity2));
        assertEqualsEventually(removed, ImmutableList.of(entity));
        
        Entities.unmanage(entity2);
        assertCollectionEquals(app.getChildren(), ImmutableList.of());
        assertEqualsEventually(removed, ImmutableList.of(entity, entity2));
    }
    
    private <T> void assertEqualsEventually(final T actual, final T expected) {
        Asserts.succeedsEventually(MutableMap.of("timeout", TIMEOUT_MS), new Runnable() {
                @Override public void run() {
                    assertEquals(actual, expected, "actual="+actual);
                }});
    }
    
    // Ignores order of vals in collection, but asserts each same size and same elements 
    private <T> void assertCollectionEquals(Collection<?> actual, Collection<?> expected) {
        assertEquals(ImmutableSet.copyOf(actual), ImmutableSet.copyOf(expected), "actual="+actual);
        assertEquals(actual.size(), expected.size(), "actual="+actual);
    }
}
