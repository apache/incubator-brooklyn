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
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicReference;

import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.BrooklynAppUnitTestSupport;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.location.LocationSpec;
import brooklyn.location.basic.SimulatedLocation;
import brooklyn.test.Asserts;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.collections.MutableSet;

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
        assertEquals(Entities.attributeSupplier(new EntityAndAttribute<String>(entity, TestEntity.NAME)).get(), "myname");
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
            .addInitializer(EntityInitializers.addingTags("foo")));
        
        entity.addTag(app);
        
        Assert.assertTrue(entity.containsTag("foo"));
        Assert.assertFalse(entity.containsTag("bar"));
        
        Assert.assertEquals(entity.getTags(), MutableSet.of(app, "foo"));
        
        entity.removeTag("foo");
        Assert.assertFalse(entity.containsTag("foo"));
        
        Assert.assertTrue(entity.containsTag(entity.getParent()));
        Assert.assertFalse(entity.containsTag(entity));
        
        Assert.assertEquals(entity.getTags(), MutableSet.of(app));
    }
    
}
