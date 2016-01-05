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

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.fail;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.mgmt.ExecutionManager;
import org.apache.brooklyn.api.mgmt.Task;
import org.apache.brooklyn.core.annotation.Effector;
import org.apache.brooklyn.core.annotation.EffectorParam;
import org.apache.brooklyn.core.effector.MethodEffector;
import org.apache.brooklyn.core.entity.AbstractEntity;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.mgmt.BrooklynTaskTags;
import org.apache.brooklyn.core.test.entity.TestApplication;
import org.apache.brooklyn.core.test.entity.TestApplicationImpl;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.core.task.BasicExecutionContext;
import org.apache.brooklyn.util.core.task.Tasks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;

public class EffectorConcatenateTest {

    
    private static final Logger log = LoggerFactory.getLogger(EffectorConcatenateTest.class);
    private static final long TIMEOUT = 10*1000;
    
    public static class MyEntityImpl extends AbstractEntity {

        public static MethodEffector<String> CONCATENATE = new MethodEffector<String>(MyEntityImpl.class, "concatenate");
        public static MethodEffector<Void> WAIT_A_BIT = new MethodEffector<Void>(MyEntityImpl.class, "waitabit");
        public static MethodEffector<Void> SPAWN_CHILD = new MethodEffector<Void>(MyEntityImpl.class, "spawnchild");

        public MyEntityImpl() {
            super();
        }
        public MyEntityImpl(Entity parent) {
            super(parent);
        }

        /** The "current task" representing the effector currently executing */
        AtomicReference<Task<?>> waitingTask = new AtomicReference<Task<?>>();
        
        /** latch is .countDown'ed by the effector at the beginning of the "waiting" point */
        CountDownLatch nowWaitingLatch = new CountDownLatch(1);
        
        /** latch is await'ed on by the effector when it is in the "waiting" point */
        CountDownLatch continueFromWaitingLatch = new CountDownLatch(1);
        
        @Effector(description="sample effector concatenating strings")
        public String concatenate(@EffectorParam(name="first", description="first argument") String first,
                @EffectorParam(name="second", description="2nd arg") String second) throws Exception {
            return first+second;
        }
        
        @Effector(description="sample effector doing some waiting")
        public void waitabit() throws Exception {
            waitingTask.set(Tasks.current());
            
            Tasks.setExtraStatusDetails("waitabit extra status details");
            
            Tasks.withBlockingDetails("waitabit.blocking", new Callable<Void>() {
                    public Void call() throws Exception {
                        nowWaitingLatch.countDown();
                        if (!continueFromWaitingLatch.await(TIMEOUT, TimeUnit.MILLISECONDS)) {
                            fail("took too long to be told to continue");
                        }
                        return null;
                    }});
        }
        
        @Effector(description="sample effector that spawns a child task that waits a bit")
        public void spawnchild() throws Exception {
            // spawn a child, then wait
            BasicExecutionContext.getCurrentExecutionContext().submit(
                    MutableMap.of("displayName", "SpawnedChildName"),
                    new Callable<Void>() {
                        public Void call() throws Exception {
                            log.info("beginning spawned child response "+Tasks.current()+", with tags "+Tasks.current().getTags());
                            Tasks.setBlockingDetails("spawned child blocking details");
                            nowWaitingLatch.countDown();
                            if (!continueFromWaitingLatch.await(TIMEOUT, TimeUnit.MILLISECONDS)) {
                                fail("took too long to be told to continue");
                            }
                            return null;
                        }});
        }
    }
            
    private TestApplication app;
    private MyEntityImpl e;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() {
        app = new TestApplicationImpl();
        e = new MyEntityImpl(app);
        Entities.startManagement(app);
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }
    
    @Test
    public void testCanInvokeEffector() throws Exception {
        // invocation map syntax
        Task<String> task = e.invoke(MyEntityImpl.CONCATENATE, ImmutableMap.of("first", "a", "second", "b"));
        assertEquals(task.get(TIMEOUT, TimeUnit.MILLISECONDS), "ab");

        // method syntax
        assertEquals("xy", e.concatenate("x", "y"));
    }
    
    @Test
    public void testReportsTaskDetails() throws Exception {
        final AtomicReference<String> result = new AtomicReference<String>();

        Thread bg = new Thread(new Runnable() {
            public void run() {
                try {
                    // Expect "wait a bit" to tell us it's blocking 
                    if (!e.nowWaitingLatch.await(TIMEOUT, TimeUnit.MILLISECONDS)) {
                        result.set("took too long for waitabit to be waiting");
                        return;
                    }

                    // Expect "wait a bit" to have retrieved and set its task
                    try {
                        Task<?> t = e.waitingTask.get();
                        String status = t.getStatusDetail(true);
                        log.info("waitabit task says:\n"+status);
                        if (!status.contains("waitabit extra status details")) {
                            result.set("Status not in expected format: doesn't contain extra status details phrase 'My extra status details'\n"+status);
                            return;
                        }
                        if (!status.startsWith("waitabit.blocking")) {
                            result.set("Status not in expected format: doesn't start with blocking details 'waitabit.blocking'\n"+status);
                            return;
                        }
                    } finally {
                        e.continueFromWaitingLatch.countDown();
                    }
                } catch (Throwable t) {
                    log.warn("Failure: "+t, t);
                    result.set("Failure: "+t);
                }
            }});
        bg.start();
    
        e.invoke(MyEntityImpl.WAIT_A_BIT, ImmutableMap.<String,Object>of())
                .get(TIMEOUT, TimeUnit.MILLISECONDS);
        
        bg.join(TIMEOUT*2);
        assertFalse(bg.isAlive());
        
        String problem = result.get();
        if (problem!=null) fail(problem);
    }
    
    @Test
    public void testReportsSpawnedTaskDetails() throws Exception {
        final AtomicReference<String> result = new AtomicReference<String>();

        Thread bg = new Thread(new Runnable() {
            public void run() {
                try {
                    // Expect "spawned child" to tell us it's blocking 
                    if (!e.nowWaitingLatch.await(TIMEOUT, TimeUnit.MILLISECONDS)) {
                        result.set("took too long for spawnchild's sub-task to be waiting");
                        return;
                    }

                    // Expect spawned task to be have been tagged with entity
                    ExecutionManager em = e.getManagementContext().getExecutionManager();
                    Task<?> subtask = Iterables.find(BrooklynTaskTags.getTasksInEntityContext(em, e), new Predicate<Task<?>>() {
                        public boolean apply(Task<?> input) {
                            return "SpawnedChildName".equals(input.getDisplayName());
                        }
                    });
                    
                    // Expect spawned task to haev correct "blocking details"
                    try {
                        String status = subtask.getStatusDetail(true);
                        log.info("subtask task says:\n"+status);
                        if (!status.contains("spawned child blocking details")) {
                            result.set("Status not in expected format: doesn't contain blocking details phrase 'spawned child blocking details'\n"+status);
                            return;
                        }
                    } finally {
                        e.continueFromWaitingLatch.countDown();
                    }
                } catch (Throwable t) {
                    log.warn("Failure: "+t, t);
                    result.set("Failure: "+t);
                }
            }});
        bg.start();
    
        e.invoke(MyEntityImpl.SPAWN_CHILD, ImmutableMap.<String,Object>of())
                .get(TIMEOUT, TimeUnit.MILLISECONDS);
        
        bg.join(TIMEOUT*2);
        assertFalse(bg.isAlive());
        
        String problem = result.get();
        if (problem!=null) fail(problem);
    }
}
