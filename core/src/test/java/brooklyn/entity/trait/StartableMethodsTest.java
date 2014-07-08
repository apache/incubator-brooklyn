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
package brooklyn.entity.trait;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.BrooklynAppUnitTestSupport;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.trait.FailingEntity.RecordingEventListener;
import brooklyn.location.basic.SimulatedLocation;
import brooklyn.management.Task;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.task.Tasks;

import com.google.common.collect.ImmutableList;

public class StartableMethodsTest extends BrooklynAppUnitTestSupport {

    private SimulatedLocation loc;
    private TestEntity entity;
    private TestEntity entity2;
    private RecordingEventListener listener;
    
    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        loc = new SimulatedLocation();
        listener = new RecordingEventListener();
    }
    
    @Test
    public void testStopSequentially() {
        entity = app.createAndManageChild(EntitySpec.create(FailingEntity.class)
                .configure(FailingEntity.LISTENER, listener));
        entity2 = app.createAndManageChild(EntitySpec.create(FailingEntity.class)
                .configure(FailingEntity.LISTENER, listener));
        app.start(ImmutableList.of(loc));
        listener.events.clear();
        
        StartableMethods.stopSequentially(ImmutableList.of(entity, entity2));
        
        assertEquals(listener.events.get(0)[0], entity);
        assertEquals(listener.events.get(1)[0], entity2);
    }
    
    @Test
    public void testStopSequentiallyContinuesOnFailure() {
        try {
            entity = app.createAndManageChild(EntitySpec.create(FailingEntity.class)
                    .configure(FailingEntity.FAIL_ON_STOP, true)
                    .configure(FailingEntity.LISTENER, listener));
            entity2 = app.createAndManageChild(EntitySpec.create(FailingEntity.class)
                    .configure(FailingEntity.LISTENER, listener));
            app.start(ImmutableList.of(loc));
            listener.events.clear();
            
            try {
                StartableMethods.stopSequentially(ImmutableList.of(entity, entity2));
                fail();
            } catch (Exception e) {
                // success; expected exception to be propagated
            }
            
            assertEquals(listener.events.get(0)[0], entity);
            assertEquals(listener.events.get(1)[0], entity2);
        } finally {
            // get rid of entity that will fail on stop, so that tearDown won't encounter exception
            Entities.unmanage(entity);
        }
    }
    
    @Test
    public void testStopSequentiallyContinuesOnFailureInSubTask() throws Exception {
        try {
            entity = app.createAndManageChild(EntitySpec.create(FailingEntity.class)
                    .configure(FailingEntity.FAIL_ON_STOP, true)
                    .configure(FailingEntity.FAIL_IN_SUB_TASK, true)
                    .configure(FailingEntity.LISTENER, listener));
            entity2 = app.createAndManageChild(EntitySpec.create(FailingEntity.class)
                    .configure(FailingEntity.LISTENER, listener));
            app.start(ImmutableList.of(loc));
            listener.events.clear();
            
            try {
                Task<?> task = Tasks.builder().name("stopSequentially")
                        .body(new Runnable() {
                            @Override public void run() {
                                StartableMethods.stopSequentially(ImmutableList.of(entity, entity2));
                            }})
                        .build();
                Entities.submit(app, task).getUnchecked();
                fail();
            } catch (Exception e) {
                // success; expected exception to be propagated
                if (!(e.toString().contains("Error stopping"))) throw e;
            }
            
            assertEquals(listener.events.get(0)[0], entity);
            assertEquals(listener.events.get(1)[0], entity2);
        } finally {
            // get rid of entity that will fail on stop, so that tearDown won't encounter exception
            Entities.unmanage(entity);
        }
    }
}
