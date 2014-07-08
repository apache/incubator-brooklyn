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
package brooklyn.entity.rebind;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import java.util.Collections;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.rebind.RebindEntityTest.MyEntity;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

public class CheckpointEntityTest extends RebindTestFixtureWithApp {

    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(CheckpointEntityTest.class);

    private MyEntity origE;
    
    @BeforeMethod
    public void setUp() throws Exception {
        super.setUp();
        origE = origApp.createAndManageChild(EntitySpec.create(MyEntity.class).configure("myconfig", "myval"));
    }

    @Test
    public void testAutoCheckpointsOnManageApp() throws Exception {
        newApp = rebind();
        MyEntity newE = (MyEntity) Iterables.find(newApp.getChildren(), Predicates.instanceOf(MyEntity.class));
        
        // Assert has expected entities and config
        assertEquals(newApp.getId(), origApp.getId());
        assertEquals(ImmutableList.copyOf(newApp.getChildren()), ImmutableList.of(newE));
        assertEquals(newE.getId(), origE.getId());
        assertEquals(newE.getConfig(MyEntity.MY_CONFIG), "myval");
    }
    
    @Test
    public void testAutoCheckpointsOnManageDynamicEntity() throws Exception {
        final MyEntity origE2 = origApp.createAndManageChild(EntitySpec.create(MyEntity.class).configure("myconfig", "myval2"));
        Entities.manage(origE2);
        
        newApp = rebind();
        MyEntity newE2 = (MyEntity) Iterables.find(newApp.getChildren(), new Predicate<Entity>() {
                @Override public boolean apply(@Nullable Entity input) {
                    return origE2.getId().equals(input.getId());
                }});
        
        // Assert has expected entities and config
        assertEquals(newApp.getChildren().size(), 2); // expect equivalent of origE and origE2
        assertEquals(newE2.getId(), origE2.getId());
        assertEquals(newE2.getConfig(MyEntity.MY_CONFIG), "myval2");
    }
    
    @Test
    public void testAutoCheckpointsOnUnmanageEntity() throws Exception {
        Entities.unmanage(origE);
        
        newApp = rebind();
        
        // Assert does not container unmanaged entity
        assertEquals(ImmutableList.copyOf(newApp.getChildren()), Collections.emptyList());
        assertNull(((EntityInternal)newApp).getManagementContext().getEntityManager().getEntity(origE.getId()));
    }
    
    @Test
    public void testPersistsOnExplicitCheckpointOfEntity() throws Exception {
        origE.setConfig(MyEntity.MY_CONFIG, "mynewval");
        origE.setAttribute(MyEntity.MY_SENSOR, "mysensorval");
        
        // Assert persisted the modified config/attributes
        newApp = rebind();
        MyEntity newE = (MyEntity) Iterables.find(newApp.getChildren(), Predicates.instanceOf(MyEntity.class));
        
        assertEquals(newE.getConfig(MyEntity.MY_CONFIG), "mynewval");
        assertEquals(newE.getAttribute(MyEntity.MY_SENSOR), "mysensorval");
    }
    
}
