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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Semaphore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.config.BrooklynProperties;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.BrooklynTaskTags;
import brooklyn.entity.basic.BrooklynTaskTags.WrappedEntity;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.management.ExecutionManager;
import brooklyn.management.Task;
import brooklyn.test.Asserts;
import brooklyn.test.entity.LocalManagementContextForTests;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.javalang.JavaClassNames;
import brooklyn.util.task.BasicExecutionManager;
import brooklyn.util.task.ExecutionListener;
import brooklyn.util.task.TaskBuilder;
import brooklyn.util.task.Tasks;
import brooklyn.util.time.Duration;
import brooklyn.util.time.Time;

import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.Callables;

/** Includes many tests for {@link BrooklynGarbageCollector} */
public class EntityExecutionManagerTest {
    
    private static final Logger LOG = LoggerFactory.getLogger(EntityExecutionManagerTest.class);
    
    private static final Duration TIMEOUT_MS = Duration.TEN_SECONDS;
    
    private ManagementContextInternal mgmt;
    private TestApplication app;
    private TestEntity e;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroyAll(app.getManagementContext());
        app = null;
        if (mgmt != null) Entities.destroyAll(mgmt);
    }

    @Test
    public void testOnDoneCallback() throws InterruptedException {
        mgmt = LocalManagementContextForTests.newInstance();
        ExecutionManager em = mgmt.getExecutionManager();
        BasicExecutionManager bem = (BasicExecutionManager)em;
        final Map<Task<?>,Duration> completedTasks = MutableMap.of();
        final Semaphore sema4 = new Semaphore(-1);
        bem.addListener(new ExecutionListener() {
            @Override
            public void onTaskDone(Task<?> task) {
                Assert.assertTrue(task.isDone());
                Assert.assertEquals(task.getUnchecked(), "foo");
                completedTasks.put(task, Duration.sinceUtc(task.getEndTimeUtc()));
                sema4.release();
            }
        });
        Task<String> t1 = em.submit( Tasks.<String>builder().name("t1").dynamic(false).body(Callables.returning("foo")).build() );
        t1.getUnchecked();
        Task<String> t2 = em.submit( Tasks.<String>builder().name("t2").dynamic(false).body(Callables.returning("foo")).build() );
        sema4.acquire();
        Assert.assertEquals(completedTasks.size(), 2, "completed tasks are: "+completedTasks);
        completedTasks.get(t1).isShorterThan(Duration.TEN_SECONDS);
        completedTasks.get(t2).isShorterThan(Duration.TEN_SECONDS);
    }
    
    protected void forceGc() {
        ((LocalManagementContext)app.getManagementContext()).getGarbageCollector().gcIteration();
    }

    protected static Task<?> runEmptyTaskWithNameAndTags(Entity target, String name, Object ...tags) {
        TaskBuilder<Object> tb = newEmptyTask(name);
        for (Object tag: tags) tb.tag(tag);
        Task<?> task = ((EntityInternal)target).getExecutionContext().submit(tb.build());
        task.getUnchecked();
        return task;
    }

    protected static TaskBuilder<Object> newEmptyTask(String name) {
        return Tasks.builder().name(name).dynamic(false).body(Callables.returning(null));
    }
    
    protected static void assertTaskCountForEntity(Entity entity, int expectedCount) {
        Collection<Task<?>> tasks = BrooklynTaskTags.getTasksInEntityContext(((EntityInternal)entity).getManagementContext().getExecutionManager(), entity);
        Assert.assertEquals(tasks.size(), expectedCount, "Tasks were "+tasks);
    }

    @Test
    public void testGetTasksAndGcBoringTags() throws Exception {
        app = TestApplication.Factory.newManagedInstanceForTests();
        e = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        
        Task<?> task = runEmptyTaskWithNameAndTags(e, "should-be-kept", ManagementContextInternal.NON_TRANSIENT_TASK_TAG);
        runEmptyTaskWithNameAndTags(e, "should-be-gcd", ManagementContextInternal.TRANSIENT_TASK_TAG);
        
        // dead task (and initialization task) should have been GC'd on completion
        Collection<Task<?>> tasks = BrooklynTaskTags.getTasksInEntityContext(app.getManagementContext().getExecutionManager(), e);
        assertEquals(tasks, ImmutableList.of(task), "Mismatched tasks, got: "+tasks);
    }

    @Test
    public void testGcTaskAtNormalTagLimit() throws Exception {
        app = TestApplication.Factory.newManagedInstanceForTests();
        e = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        
        ((BrooklynProperties)app.getManagementContext().getConfig()).put(
            BrooklynGarbageCollector.MAX_TASKS_PER_TAG, 2);

        for (int count=0; count<5; count++)
            runEmptyTaskWithNameAndTags(e, "task"+count, ManagementContextInternal.NON_TRANSIENT_TASK_TAG, "boring-tag");

        forceGc();
        assertTaskCountForEntity(e, 2);
    }
    
    @Test
    public void testGcTaskAtEntityLimit() throws Exception {
        app = TestApplication.Factory.newManagedInstanceForTests();
        e = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        
        ((BrooklynProperties)app.getManagementContext().getConfig()).put(
            BrooklynGarbageCollector.MAX_TASKS_PER_ENTITY, 2);
        
        for (int count=0; count<5; count++)
            runEmptyTaskWithNameAndTags(e, "task-e-"+count, ManagementContextInternal.NON_TRANSIENT_TASK_TAG, "boring-tag");
        for (int count=0; count<5; count++)
            runEmptyTaskWithNameAndTags(app, "task-app-"+count, ManagementContextInternal.NON_TRANSIENT_TASK_TAG, "boring-tag");
        
        forceGc();
        assertTaskCountForEntity(app, 2);
        assertTaskCountForEntity(e, 2);
    }
    
    @Test
    public void testGcTaskWithTagAndEntityLimit() throws Exception {
        app = TestApplication.Factory.newManagedInstanceForTests();
        e = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        
        ((BrooklynProperties)app.getManagementContext().getConfig()).put(
            BrooklynGarbageCollector.MAX_TASKS_PER_ENTITY, 6);
        ((BrooklynProperties)app.getManagementContext().getConfig()).put(
            BrooklynGarbageCollector.MAX_TASKS_PER_TAG, 2);

        int count=0;
        
        runEmptyTaskWithNameAndTags(app, "task-"+(count++), ManagementContextInternal.NON_TRANSIENT_TASK_TAG, "boring-tag");
        runEmptyTaskWithNameAndTags(e, "task-"+(count++), ManagementContextInternal.NON_TRANSIENT_TASK_TAG, "boring-tag");
        Time.sleep(Duration.ONE_MILLISECOND);
        // should keep the 2 below, because all the other borings get grace, but delete the ones above
        runEmptyTaskWithNameAndTags(e, "task-"+(count++), ManagementContextInternal.NON_TRANSIENT_TASK_TAG, "boring-tag");
        runEmptyTaskWithNameAndTags(e, "task-"+(count++), ManagementContextInternal.NON_TRANSIENT_TASK_TAG, "boring-tag");
        
        runEmptyTaskWithNameAndTags(e, "task-"+(count++), ManagementContextInternal.NON_TRANSIENT_TASK_TAG, "boring-tag", "another-tag-e");
        runEmptyTaskWithNameAndTags(e, "task-"+(count++), ManagementContextInternal.NON_TRANSIENT_TASK_TAG, "boring-tag", "another-tag-e");
        // should keep both the above
        
        runEmptyTaskWithNameAndTags(e, "task-"+(count++), ManagementContextInternal.NON_TRANSIENT_TASK_TAG, "another-tag");
        runEmptyTaskWithNameAndTags(e, "task-"+(count++), ManagementContextInternal.NON_TRANSIENT_TASK_TAG, "another-tag");
        Time.sleep(Duration.ONE_MILLISECOND);
        runEmptyTaskWithNameAndTags(app, "task-"+(count++), ManagementContextInternal.NON_TRANSIENT_TASK_TAG, "another-tag");
        // should keep the below since they have unique tags, but remove one of the e tasks above 
        runEmptyTaskWithNameAndTags(e, "task-"+(count++), ManagementContextInternal.NON_TRANSIENT_TASK_TAG, "another-tag", "and-another-tag");
        runEmptyTaskWithNameAndTags(app, "task-"+(count++), ManagementContextInternal.NON_TRANSIENT_TASK_TAG, "another-tag-app", "another-tag");
        runEmptyTaskWithNameAndTags(app, "task-"+(count++), ManagementContextInternal.NON_TRANSIENT_TASK_TAG, "another-tag-app", "another-tag");
        
        forceGc();
        assertTaskCountForEntity(e, 6);
        assertTaskCountForEntity(app, 3);
        
        // now with a lowered limit, we should remove one more e
        ((BrooklynProperties)app.getManagementContext().getConfig()).put(
            BrooklynGarbageCollector.MAX_TASKS_PER_ENTITY, 5);
        forceGc();
        assertTaskCountForEntity(e, 5);
    }
    
    @Test
    public void testGcDynamicTaskAtNormalTagLimit() throws Exception {
        app = TestApplication.Factory.newManagedInstanceForTests();
        e = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        
        ((BrooklynProperties)app.getManagementContext().getConfig()).put(
            BrooklynGarbageCollector.MAX_TASKS_PER_TAG, 2);

        for (int count=0; count<5; count++) {
            TaskBuilder<Object> tb = Tasks.builder().name("task-"+count).dynamic(true).body(new Runnable() { @Override public void run() {}})
                .tag(ManagementContextInternal.NON_TRANSIENT_TASK_TAG).tag("foo");
            ((EntityInternal)e).getExecutionContext().submit(tb.build()).getUnchecked();
        }

        forceGc();

        // might need an eventually here, if the internal job completion and GC is done in the background
        // (if there are no test failures for a few months, since Sept 2014, then we can remove this comment)
        assertTaskCountForEntity(e, 2);
    }
    
    @Test
    public void testUnmanagedEntityCanBeGcedEvenIfPreviouslyTagged() throws Exception {
        app = TestApplication.Factory.newManagedInstanceForTests();
        e = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        String eId = e.getId();
        
        e.invoke(TestEntity.MY_EFFECTOR, ImmutableMap.<String,Object>of()).get();
        Set<Task<?>> tasks = BrooklynTaskTags.getTasksInEntityContext(app.getManagementContext().getExecutionManager(), e);
        Task<?> task = Iterables.get(tasks, 0);
        assertTrue(task.getTags().contains(BrooklynTaskTags.tagForContextEntity(e)));

        Set<Object> tags = app.getManagementContext().getExecutionManager().getTaskTags();
        assertTrue(tags.contains(BrooklynTaskTags.tagForContextEntity(e)), "tags="+tags);
        
        Entities.destroy(e);
        
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
    public void testSubscriptionAndEffectorTasksGced() throws Exception {
        app = TestApplication.Factory.newManagedInstanceForTests();
        BasicExecutionManager em = (BasicExecutionManager) app.getManagementContext().getExecutionManager();
        // allow background enrichers to complete
        Time.sleep(Duration.ONE_SECOND);
        forceGc();
        List<Task<?>> t1 = em.getAllTasks();
        
        TestEntity entity = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        entity.setAttribute(TestEntity.NAME, "bob");
        entity.invoke(TestEntity.MY_EFFECTOR, ImmutableMap.<String,Object>of()).get();
        Entities.destroy(entity);
        Time.sleep(Duration.ONE_SECOND);
        forceGc();
        List<Task<?>> t2 = em.getAllTasks();
        
        Assert.assertEquals(t1.size(), t2.size(), "lists are different:\n"+t1+"\n"+t2+"\n");
    }

    /**
     * Invoke effector many times, where each would claim 10MB because it stores the return value.
     * If it didn't gc the tasks promptly, it would consume 10GB ram (so would OOME before that).
     */
    @Test(groups="Integration")
    public void testEffectorTasksGcedSoNoOome() throws Exception {
        
        BrooklynProperties brooklynProperties = BrooklynProperties.Factory.newEmpty();
        brooklynProperties.put(BrooklynGarbageCollector.GC_PERIOD, Duration.ONE_MILLISECOND);
        brooklynProperties.put(BrooklynGarbageCollector.MAX_TASKS_PER_TAG, 2);
        
        app = ApplicationBuilder.newManagedApp(TestApplication.class, LocalManagementContextForTests.newInstance(brooklynProperties));
        TestEntity entity = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        
        for (int i = 0; i < 1000; i++) {
            if (i%100==0) LOG.info(JavaClassNames.niceClassAndMethod()+": iteration "+i);
            try {
                LOG.debug("testEffectorTasksGced: iteration="+i);
                entity.invoke(TestEntity.IDENTITY_EFFECTOR, ImmutableMap.of("arg", new BigObject(10*1000*1000))).get();
                
                Time.sleep(Duration.ONE_MILLISECOND); // Give GC thread a chance to run
                forceGc();
            } catch (OutOfMemoryError e) {
                LOG.warn(JavaClassNames.niceClassAndMethod()+": OOME at iteration="+i);
                throw e;
            }
        }
    }
    
    @Test(groups="Integration")
    public void testUnmanagedEntityGcedOnUnmanageEvenIfEffectorInvoked() throws Exception {
        app = TestApplication.Factory.newManagedInstanceForTests();
        
        BasicAttributeSensor<Object> byteArrayAttrib = new BasicAttributeSensor<Object>(Object.class, "test.byteArray", "");

        for (int i = 0; i < 1000; i++) {
            if (i%100==0) LOG.info(JavaClassNames.niceClassAndMethod()+": iteration "+i);
            try {
                LOG.debug(JavaClassNames.niceClassAndMethod()+": iteration="+i);
                TestEntity entity = app.createAndManageChild(EntitySpec.create(TestEntity.class));
                entity.setAttribute(byteArrayAttrib, new BigObject(10*1000*1000));
                entity.invoke(TestEntity.MY_EFFECTOR, ImmutableMap.<String,Object>of()).get();
                Entities.destroy(entity);
                forceGc();
                System.gc(); System.gc();
            } catch (OutOfMemoryError e) {
                LOG.warn(JavaClassNames.niceClassAndMethod()+": OOME at iteration="+i);
                ExecutionManager em = app.getManagementContext().getExecutionManager();
                Collection<Task<?>> tasks = ((BasicExecutionManager)em).getAllTasks();
                LOG.info("TASKS count "+tasks.size()+": "+tasks);
                throw e;
            }
        }
    }
    
    @Test(groups={"Integration"})
    public void testEffectorTasksGcedForMaxPerTag() throws Exception {
        int maxNumTasks = 2;
        BrooklynProperties brooklynProperties = BrooklynProperties.Factory.newEmpty();
        brooklynProperties.put(BrooklynGarbageCollector.GC_PERIOD, Duration.ONE_SECOND);
        brooklynProperties.put(BrooklynGarbageCollector.MAX_TASKS_PER_TAG, 2);
        
        app = ApplicationBuilder.newManagedApp(TestApplication.class, LocalManagementContextForTests.newInstance(brooklynProperties));
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
        Duration maxTaskAge = Duration.millis(100);
        Duration maxOverhead = Duration.millis(250);
        Duration earlyReturnGrace = Duration.millis(10);
        BrooklynProperties brooklynProperties = BrooklynProperties.Factory.newEmpty();
        brooklynProperties.put(BrooklynGarbageCollector.GC_PERIOD, Duration.ONE_MILLISECOND);
        brooklynProperties.put(BrooklynGarbageCollector.MAX_TASK_AGE, maxTaskAge);
        
        app = ApplicationBuilder.newManagedApp(TestApplication.class, LocalManagementContextForTests.newInstance(brooklynProperties));
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

        Duration timeToGc = Duration.of(stopwatch);
        assertTrue(timeToGc.isLongerThan(maxTaskAge.subtract(earlyReturnGrace)), "timeToGc="+timeToGc+"; maxTaskAge="+maxTaskAge);
        assertTrue(timeToGc.isShorterThan(maxTaskAge.add(maxOverhead)), "timeToGc="+timeToGc+"; maxTaskAge="+maxTaskAge);
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
            return "BigObject["+sizeBytes+"/"+data.length+"]";
        }
    }
}
