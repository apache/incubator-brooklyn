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
package org.apache.brooklyn.core.effector;

import java.util.List;
import java.util.concurrent.Callable;

import org.apache.brooklyn.api.entity.EntityLocal;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.mgmt.HasTaskChildren;
import org.apache.brooklyn.api.mgmt.Task;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.trait.FailingEntity;
import org.apache.brooklyn.core.entity.trait.Startable;
import org.apache.brooklyn.core.location.SimulatedLocation;
import org.apache.brooklyn.core.mgmt.internal.ManagementContextInternal;
import org.apache.brooklyn.core.test.BrooklynAppUnitTestSupport;
import org.apache.brooklyn.core.test.entity.TestEntity;
import org.apache.brooklyn.test.Asserts;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.core.task.Tasks;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

public class EffectorBasicTest extends BrooklynAppUnitTestSupport {

    private static final Logger log = LoggerFactory.getLogger(EffectorBasicTest.class);
    
    // NB: more tests of effectors in EffectorSayHiTest and EffectorConcatenateTest
    // as well as EntityConfigMapUsageTest and others

    private List<SimulatedLocation> locs;
    
    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        locs = ImmutableList.of(new SimulatedLocation());
    }
    
    @Test
    public void testInvokeEffectorStart() {
        app.start(locs);
        Asserts.assertEqualsIgnoringOrder(locs, app.getLocations());
        // TODO above does not get registered as a task
    }

    @Test
    public void testInvokeEffectorStartWithMap() {
        app.invoke(Startable.START, MutableMap.of("locations", locs)).getUnchecked();
        Asserts.assertEqualsIgnoringOrder(locs, app.getLocations());
    }

    @Test
    public void testInvokeEffectorStartWithArgs() {
        Entities.invokeEffectorWithArgs((EntityLocal)app, app, Startable.START, locs).getUnchecked();
        Asserts.assertEqualsIgnoringOrder(locs, app.getLocations());
    }

    @Test
    public void testInvokeEffectorStartWithTwoEntities() {
        TestEntity entity = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        TestEntity entity2 = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        app.start(locs);
        Asserts.assertEqualsIgnoringOrder(locs, app.getLocations());
        Asserts.assertEqualsIgnoringOrder(locs, entity.getLocations());
        Asserts.assertEqualsIgnoringOrder(locs, entity2.getLocations());
    }
    
    @Test
    public void testInvokeEffectorTaskHasTag() {
        Task<Void> starting = app.invoke(Startable.START, MutableMap.of("locations", locs));
//        log.info("TAGS: "+starting.getTags());
        Assert.assertTrue(starting.getTags().contains(ManagementContextInternal.EFFECTOR_TAG));
    }

    // check various failure situations
    
    private FailingEntity createFailingEntity() {
        FailingEntity entity = app.createAndManageChild(EntitySpec.create(FailingEntity.class)
            .configure(FailingEntity.FAIL_ON_START, true));
        return entity;
    }

    // uncaught failures are propagates
    
    @Test
    public void testInvokeEffectorStartFailing_Method() {
        FailingEntity entity = createFailingEntity();
        assertStartMethodFails(entity);
    }

    @Test
    public void testInvokeEffectorStartFailing_EntityInvoke() {
        FailingEntity entity = createFailingEntity();
        assertTaskFails( entity.invoke(Startable.START, MutableMap.of("locations", locs)) );
    }
     
    @Test
    public void testInvokeEffectorStartFailing_EntitiesInvoke() {
        FailingEntity entity = createFailingEntity();
        
        assertTaskFails( Entities.invokeEffectorWithArgs(entity, entity, Startable.START, locs) );
    }

    // caught failures are NOT propagated!
    
    @Test
    public void testInvokeEffectorStartFailing_MethodInDynamicTask() {
        Task<Void> task = app.getExecutionContext().submit(Tasks.<Void>builder().dynamic(true).body(new Callable<Void>() {
            @Override public Void call() throws Exception {
                testInvokeEffectorStartFailing_Method();
                return null;
            }
        }).build());
        
        assertTaskSucceeds(task);
        assertTaskHasFailedChild(task);
    }

    @Test
    public void testInvokeEffectorStartFailing_MethodInTask() {
        Task<Void> task = app.getExecutionContext().submit(Tasks.<Void>builder().dynamic(false).body(new Callable<Void>() {
            @Override public Void call() throws Exception {
                testInvokeEffectorStartFailing_Method();
                return null;
            }
        }).build());
        
        assertTaskSucceeds(task);
    }

    private void assertTaskSucceeds(Task<Void> task) {
        task.getUnchecked();
        Assert.assertFalse(task.isError());
    }

    private void assertTaskHasFailedChild(Task<Void> task) {
        Assert.assertTrue(Tasks.failed( ((HasTaskChildren)task).getChildren() ).iterator().hasNext());
    }
        
    private void assertStartMethodFails(FailingEntity entity) {
        try {
            entity.start(locs);
            Assert.fail("Should have failed");
        } catch (Exception e) {
            // expected
        }
    }
     
    protected void assertTaskFails(Task<?> t) {
        try {
            t.get();
            Assert.fail("Should have failed");
        } catch (Exception e) {
            Exceptions.propagateIfFatal(e);
            // expected
        }
    }
    
}
