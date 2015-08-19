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
package org.apache.brooklyn.core.entity;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicReference;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.LocationSpec;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.EntityAndAttribute;
import org.apache.brooklyn.core.entity.EntityInitializers;
import org.apache.brooklyn.core.test.BrooklynAppUnitTestSupport;
import org.apache.brooklyn.core.test.entity.TestEntity;
import org.apache.brooklyn.test.Asserts;
import org.apache.brooklyn.util.collections.MutableSet;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.apache.brooklyn.location.core.SimulatedLocation;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

public class EntitiesTest extends BrooklynAppUnitTestSupport {

    private static final int TIMEOUT_MS = 10*1000;
    
    private SimulatedLocation loc;
    private TestEntity entity;
    
    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        loc = app.getManagementContext().getLocationManager().createLocation(LocationSpec.create(SimulatedLocation.class));
        entity = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        app.start(ImmutableList.of(loc));
    }
    
    @Test
    public void testDescendants() throws Exception {
        Assert.assertEquals(Iterables.size(Entities.descendants(app)), 2);
        Assert.assertEquals(Iterables.getOnlyElement(Entities.descendants(app, TestEntity.class)), entity);
    }
    
    @Test
    public void testAttributeSupplier() throws Exception {
        entity.setAttribute(TestEntity.NAME, "myname");
        assertEquals(Entities.attributeSupplier(entity, TestEntity.NAME).get(), "myname");
    }
    
    @Test
    public void testAttributeSupplierUsingTuple() throws Exception {
        entity.setAttribute(TestEntity.NAME, "myname");
        assertEquals(Entities.attributeSupplier(EntityAndAttribute.supplier(entity, TestEntity.NAME)).get(), "myname");
    }
    
    @Test(groups="Integration") // takes 1 second
    public void testAttributeSupplierWhenReady() throws Exception {
        final AtomicReference<String> result = new AtomicReference<String>();
        
        final Thread t = new Thread(new Runnable() {
            @Override public void run() {
                result.set(Entities.attributeSupplierWhenReady(entity, TestEntity.NAME).get());
                
            }});
        try {
            t.start();
            
            // should block, waiting for value
            Asserts.succeedsContinually(new Runnable() {
                @Override public void run() {
                    assertTrue(t.isAlive());
                }
            });
            
            entity.setAttribute(TestEntity.NAME, "myname");
            t.join(TIMEOUT_MS);
            assertFalse(t.isAlive());
            assertEquals(result.get(), "myname");
        } finally {
            t.interrupt();
        }
        
        // And now that it's set, the attribute-when-ready should return immediately
        assertEquals(Entities.attributeSupplierWhenReady(entity, TestEntity.NAME).get(), "myname");
    }
    
    @Test
    public void testCreateGetContainsAndRemoveTags() throws Exception {
        entity = app.createAndManageChild(EntitySpec.create(TestEntity.class)
            .tag(2)
            .addInitializer(EntityInitializers.addingTags("foo")));
        
        entity.tags().addTag(app);
        
        Assert.assertTrue(entity.tags().containsTag(app));
        Assert.assertTrue(entity.tags().containsTag("foo"));
        Assert.assertTrue(entity.tags().containsTag(2));
        Assert.assertFalse(entity.tags().containsTag("bar"));
        
        Assert.assertEquals(entity.tags().getTags(), MutableSet.of(app, "foo", 2));
        
        entity.tags().removeTag("foo");
        Assert.assertFalse(entity.tags().containsTag("foo"));
        
        Assert.assertTrue(entity.tags().containsTag(entity.getParent()));
        Assert.assertFalse(entity.tags().containsTag(entity));
        
        entity.tags().removeTag(2);
        Assert.assertEquals(entity.tags().getTags(), MutableSet.of(app));
    }
    
}
