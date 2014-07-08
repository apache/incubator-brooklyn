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
package brooklyn.management.internal;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.config.BrooklynProperties;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.BrooklynTaskTags;
import brooklyn.entity.basic.BrooklynTaskTags.WrappedEntity;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.management.Task;
import brooklyn.test.Asserts;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.collections.MutableMap;

import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

public class EntityExecutionManagerTest {
    
    private static final Logger LOG = LoggerFactory.getLogger(EntityExecutionManagerTest.class);
    
    private static final int TIMEOUT_MS = 10*1000;
    
    private TestApplication app;
    private TestEntity e;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroyAll(app.getManagementContext());
        app = null;
    }

    @Test
    public void testGetTasksOfEntity() throws Exception {
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        e = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        
        final CountDownLatch latch = new CountDownLatch(1);
        Task<?> task = e.getExecutionContext().submit(
                MutableMap.of("tag", ManagementContextInternal.NON_TRANSIENT_TASK_TAG),
                new Runnable() {
                    @Override public void run() {
                        latch.countDown();
                    }});
        latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        
        Collection<Task<?>> tasks = BrooklynTaskTags.getTasksInEntityContext(app.getManagementContext().getExecutionManager(), e);
        assertEquals(tasks, ImmutableList.of(task));
    }
    
    @Test
    public void testUnmanagedEntityCanBeGcedEvenIfPreviouslyTagged() throws Exception {
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        e = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        String eId = e.getId();
        
        e.invoke(TestEntity.MY_EFFECTOR, ImmutableMap.<String,Object>of()).get();
        Set<Task<?>> tasks = BrooklynTaskTags.getTasksInEntityContext(app.getManagementContext().getExecutionManager(), e);
        Task<?> task = Iterables.get(tasks, 0);
        assertTrue(task.getTags().contains(BrooklynTaskTags.tagForContextEntity(e)));

        Set<Object> tags = app.getManagementContext().getExecutionManager().getTaskTags();
        assertTrue(tags.contains(BrooklynTaskTags.tagForContextEntity(e)), "tags="+tags);
        
        Entities.destroy(e);
        e = null;
        for (int i = 0; i < 5; i++) System.gc();
        
        Set<Object> tags2 = app.getManagementContext().getExecutionManager().getTaskTags();
        for (Object tag : tags2) {
            if (tag instanceof Entity && ((Entity)tag).getId().equals(eId)) {
                fail("tags contains unmanaged entity "+tag);
            }
            if ((tag instanceof WrappedEntity) && ((WrappedEntity)tag).entity.getId().equals(eId) 
                    && ((WrappedEntity)tag).wrappingType.equals(BrooklynTaskTags.CONTEXT_ENTITY)) {
                fail("tags contains unmanaged entity (wrapped) "+tag);
            }
        }
        return;
    }
    
    @Test(groups="Integration")
    public void testUnmanagedEntityGcedOnUnmanageEvenIfEffectorInvoked() throws Exception {
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        
        BasicAttributeSensor<Object> byteArrayAttrib = new BasicAttributeSensor<Object>(Object.class, "test.byteArray", "");

        for (int i = 0; i < 1000; i++) {
            try {
                LOG.debug("testUnmanagedEntityGcedOnUnmanageEvenIfEffectorInvoked: iteration="+i);
                TestEntity entity = app.createAndManageChild(EntitySpec.create(TestEntity.class));
                entity.setAttribute(byteArrayAttrib, new BigObject(10*1000*1000));
                entity.invoke(TestEntity.MY_EFFECTOR, ImmutableMap.<String,Object>of()).get();
                Entities.destroy(entity);
            } catch (OutOfMemoryError e) {
                LOG.info("testUnmanagedEntityGcedOnUnmanageEvenIfEffectorInvoked: OOME at iteration="+i);
                throw e;
            }
        }
    }
    
    /**
     * Invoke effector many times, where each would claim 10MB because it stores the return value.
     * If it didn't gc the tasks promptly, it would consume 10GB ram (so would OOME before that).
     */
    @Test(groups="Integration")
    public void testEffectorTasksGcedSoNoOome() throws Exception {
        
        BrooklynProperties brooklynProperties = BrooklynProperties.Factory.newEmpty();
        brooklynProperties.put(BrooklynGarbageCollector.GC_PERIOD, 1);
        brooklynProperties.put(BrooklynGarbageCollector.MAX_TASKS_PER_TAG, 2);
        
        app = ApplicationBuilder.newManagedApp(TestApplication.class, new LocalManagementContext(brooklynProperties));
        TestEntity entity = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        
        for (int i = 0; i < 1000; i++) {
            try {
                LOG.debug("testEffectorTasksGced: iteration="+i);
                entity.invoke(TestEntity.IDENTITY_EFFECTOR, ImmutableMap.of("arg", new BigObject(10*1000*1000))).get();
                
                Thread.sleep(1); // Give GC thread a chance to run
                
            } catch (OutOfMemoryError e) {
                LOG.info("testEffectorTasksGced: OOME at iteration="+i);
                throw e;
            }
        }
    }
    
    // FIXME DynamicSequentialTask creates a secondaryJobMaster task (DstJob) so we have these extra tasks interfering.
    // We can't just mark that task as transient, as all sub-tasks of the sequential-task have that as its
    // context so are automatically deleted. We probably don't want to make that secondaryJobMaster a child of the 
    // DynamicSequentialTask to have it deleted automatically because then it would be listed in the web-console's 
    // task view.
    // The "right" solution is probably to get rid of that task altogether, and rely on the newTaskEndCallback.
    @Test(groups={"Integration", "WIP"})
    public void testEffectorTasksGcedForMaxPerTag() throws Exception {
        int maxNumTasks = 2;
        BrooklynProperties brooklynProperties = BrooklynProperties.Factory.newEmpty();
        brooklynProperties.put(BrooklynGarbageCollector.GC_PERIOD, 1000);
        brooklynProperties.put(BrooklynGarbageCollector.MAX_TASKS_PER_TAG, 2);
        
        app = ApplicationBuilder.newManagedApp(TestApplication.class, new LocalManagementContext(brooklynProperties));
        final TestEntity entity = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        
        List<Task<?>> tasks = Lists.newArrayList();
        
        for (int i = 0; i < (maxNumTasks+1); i++) {
            Task<?> task = entity.invoke(TestEntity.MY_EFFECTOR, ImmutableMap.<String,Object>of());
            task.get();
            tasks.add(task);
        }
        
        // Should initially have all tasks
        Set<Task<?>> storedTasks = app.getManagementContext().getExecutionManager().getTasksWithAllTags(
                ImmutableList.of(BrooklynTaskTags.tagForContextEntity(entity), ManagementContextInternal.EFFECTOR_TAG));
        assertEquals(storedTasks, ImmutableSet.copyOf(tasks), "storedTasks="+storedTasks+"; expected="+tasks);
        
        // Then oldest should be GC'ed to leave only maxNumTasks
        final List<Task<?>> recentTasks = tasks.subList(tasks.size()-maxNumTasks, tasks.size());
        Asserts.succeedsEventually(ImmutableMap.of("timeout", TIMEOUT_MS), new Runnable() {
            @Override public void run() {
                Set<Task<?>> storedTasks2 = app.getManagementContext().getExecutionManager().getTasksWithAllTags(
                       ImmutableList.of(BrooklynTaskTags.tagForContextEntity(entity), ManagementContextInternal.EFFECTOR_TAG));
                assertEquals(storedTasks2, ImmutableSet.copyOf(recentTasks), "storedTasks="+storedTasks2+"; expected="+recentTasks);
            }});
    }
    
    @Test(groups="Integration")
    public void testEffectorTasksGcedForAge() throws Exception {
        int maxTaskAge = 100;
        int maxOverhead = 250;
        int earlyReturnGrace = 10;
        BrooklynProperties brooklynProperties = BrooklynProperties.Factory.newEmpty();
        brooklynProperties.put(BrooklynGarbageCollector.GC_PERIOD, 1);
        brooklynProperties.put(BrooklynGarbageCollector.MAX_TASK_AGE, maxTaskAge);
        
        app = ApplicationBuilder.newManagedApp(TestApplication.class, new LocalManagementContext(brooklynProperties));
        final TestEntity entity = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        
        Stopwatch stopwatch = Stopwatch.createStarted();
        Task<?> oldTask = entity.invoke(TestEntity.MY_EFFECTOR, ImmutableMap.<String,Object>of());
        oldTask.get();
        
        Asserts.succeedsEventually(ImmutableMap.of("timeout", TIMEOUT_MS), new Runnable() {
            @Override public void run() {
                Set<Task<?>> storedTasks = app.getManagementContext().getExecutionManager().getTasksWithAllTags(ImmutableList.of(
                        BrooklynTaskTags.tagForTargetEntity(entity), 
                        ManagementContextInternal.EFFECTOR_TAG));
                assertEquals(storedTasks, ImmutableSet.of(), "storedTasks="+storedTasks);
            }});

        long timeToGc = stopwatch.elapsed(TimeUnit.MILLISECONDS);
        
        assertTrue(timeToGc > (maxTaskAge-earlyReturnGrace), "timeToGc="+timeToGc+"; maxTaskAge="+maxTaskAge);
        assertTrue(timeToGc < (maxTaskAge+maxOverhead), "timeToGc="+timeToGc+"; maxTaskAge="+maxTaskAge);
    }
    
    private static class BigObject implements Serializable {
        private static final long serialVersionUID = -4021304829674972215L;
        private final int sizeBytes;
        private final byte[] data;
        
        BigObject(int sizeBytes) {
            this.sizeBytes = sizeBytes;
            this.data = new byte[sizeBytes];
        }
        
        @Override
        public String toString() {
            return "BigObject["+sizeBytes+"]";
        }
    }
}
