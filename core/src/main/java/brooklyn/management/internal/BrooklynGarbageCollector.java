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

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.brooklyn.management.HasTaskChildren;
import org.apache.brooklyn.management.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.BrooklynProperties;
import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.BrooklynTaskTags;
import brooklyn.entity.basic.BrooklynTaskTags.WrappedEntity;
import brooklyn.entity.basic.BrooklynTaskTags.WrappedStream;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.Entities;
import brooklyn.internal.storage.BrooklynStorage;
import brooklyn.location.Location;
import brooklyn.util.collections.MutableList;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.collections.MutableSet;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.javalang.MemoryUsageTracker;
import brooklyn.util.task.BasicExecutionManager;
import brooklyn.util.task.ExecutionListener;
import brooklyn.util.task.Tasks;
import brooklyn.util.text.Strings;
import brooklyn.util.time.Duration;

import com.google.common.base.Objects;
import com.google.common.annotations.Beta;
import com.google.common.collect.Iterables;

/**
 * Deletes record of old tasks, to prevent space leaks and the eating up of more and more memory.
 * 
 * The deletion policy is configurable:
 * <ul>
 *   <li>Period - how frequently to look at the existing tasks to delete some, if required
 *   <li>Max task age - the time after which a completed task will be automatically deleted
 *       (i.e. any root task completed more than maxTaskAge ago will be deleted)
 *   <li>Max tasks per <various categories> - the maximum number of tasks to be kept for a given tag,
 *       split into categories based on what is seeming to be useful
 * </ul>
 * 
 * The default is to check with a period of one minute, deleting tasks after 30 days, 
 * and keeping at most 100000 tasks in the system,
 * max 1000 tasks per entity, 50 per effector within that entity, and 50 per other non-effector tag
 * within that entity (or global if not attached to an entity).
 * 
 * @author aled
 */
public class BrooklynGarbageCollector {

    private static final Logger LOG = LoggerFactory.getLogger(BrooklynGarbageCollector.class);

    public static final ConfigKey<Duration> GC_PERIOD = ConfigKeys.newDurationConfigKey(
            "brooklyn.gc.period", "the period for checking if any tasks need to be deleted", 
            Duration.minutes(1));
    
    public static final ConfigKey<Boolean> DO_SYSTEM_GC = ConfigKeys.newBooleanConfigKey(
            "brooklyn.gc.doSystemGc", "whether to periodically call System.gc()", false);
    
    /** 
     * should we check for tasks which are submitted by another but backgrounded, i.e. not a child of that task?
     * default to yes, despite it can be some extra loops, to make sure we GC them promptly.
     * @since 0.7.0 */
    // work offender is {@link DynamicSequentialTask} internal job tracker, but it is marked 
    // transient so it is destroyed prompty; there may be others, however;
    // but OTOH it might be expensive to check for these all the time!
    // TODO probably we can set this false (remove this and related code),
    // and just rely on usual GC to pick up background tasks; the lifecycle of background task
    // should normally be independent of the submitter. (DST was the exception, and marking 
    // transient there fixes the main problem, which is when the submitter is GC'd but the submitted is not,
    // and we don't want the submitted to show up at the root in the GUI, which it will if its
    // submitter has been GC'd)
    @Beta
    public static final ConfigKey<Boolean> CHECK_SUBTASK_SUBMITTERS = ConfigKeys.newBooleanConfigKey(
        "brooklyn.gc.checkSubtaskSubmitters", "whether for subtasks to check the submitters", true);

    public static final ConfigKey<Integer> MAX_TASKS_PER_TAG = ConfigKeys.newIntegerConfigKey(
        "brooklyn.gc.maxTasksPerTag", 
        "the maximum number of tasks to be kept for a given tag "
        + "within an execution context (e.g. entity); "
        + "some broad-brush tags are excluded, and if an entity has multiple tags all tag counts must be full",
        50);
    
    public static final ConfigKey<Integer> MAX_TASKS_PER_ENTITY = ConfigKeys.newIntegerConfigKey(
        "brooklyn.gc.maxTasksPerEntity", 
        "the maximum number of tasks to be kept for a given entity",
        1000);

    public static final ConfigKey<Integer> MAX_TASKS_GLOBAL = ConfigKeys.newIntegerConfigKey(
        "brooklyn.gc.maxTasksGlobal", 
        "the maximum number of tasks to be kept across the entire system",
        100000);

    public static final ConfigKey<Duration> MAX_TASK_AGE = ConfigKeys.newDurationConfigKey(
            "brooklyn.gc.maxTaskAge", 
            "the duration after which a completed task will be automatically deleted", 
            Duration.days(30));
    
    protected final static Comparator<Task<?>> TASKS_OLDEST_FIRST_COMPARATOR = new Comparator<Task<?>>() {
        @Override public int compare(Task<?> t1, Task<?> t2) {
            long end1 = t1.getEndTimeUtc();
            long end2 = t2.getEndTimeUtc();
            return (end1 < end2) ? -1 : ((end1 == end2) ? 0 : 1);
        }
    };
    
    private final BasicExecutionManager executionManager;
    private final BrooklynStorage storage;
    private final BrooklynProperties brooklynProperties;
    private final ScheduledExecutorService executor;
    private ScheduledFuture<?> activeCollector;
    private Map<Entity,Task<?>> unmanagedEntitiesNeedingGc = new LinkedHashMap<Entity, Task<?>>();
    
    private Duration gcPeriod;
    private final boolean doSystemGc;
    private volatile boolean running = true;
    
    public BrooklynGarbageCollector(BrooklynProperties brooklynProperties, BasicExecutionManager executionManager, BrooklynStorage storage) {
        this.executionManager = executionManager;
        this.storage = storage;
        this.brooklynProperties = brooklynProperties;

        doSystemGc = brooklynProperties.getConfig(DO_SYSTEM_GC);
        
        executor = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
                @Override public Thread newThread(Runnable r) {
                    return new Thread(r, "brooklyn-gc");
                }});
        
        executionManager.addListener(new ExecutionListener() {
                @Override public void onTaskDone(Task<?> task) {
                    BrooklynGarbageCollector.this.onTaskDone(task);
                }});
    
        scheduleCollector(true);
    }

    protected synchronized void scheduleCollector(boolean canInterruptCurrent) {
        if (activeCollector != null) activeCollector.cancel(canInterruptCurrent);
        
        gcPeriod = brooklynProperties.getConfig(GC_PERIOD);
        if (gcPeriod!=null) {
            activeCollector = executor.scheduleWithFixedDelay(
                new Runnable() {
                    @Override public void run() {
                        gcIteration();
                    }
                }, 
                gcPeriod.toMillisecondsRoundingUp(), 
                gcPeriod.toMillisecondsRoundingUp(), 
                TimeUnit.MILLISECONDS);
        }
    }

    /** force a round of Brooklyn garbage collection */
    public void gcIteration() {
        try {
            logUsage("brooklyn gc (before)");
            gcTasks();
            logUsage("brooklyn gc (after)");
            
            if (doSystemGc) {
                // Can be very useful when tracking down OOMEs etc, where a lot of tasks are executing
                // Empirically observed that (on OS X jvm at least) calling twice blocks - logs a significant
                // amount of memory having been released, as though a full-gc had been run. But this is highly
                // dependent on the JVM implementation.
                System.gc(); System.gc();
                logUsage("brooklyn gc (after system gc)");
            }
        } catch (Throwable t) {
            Exceptions.propagateIfFatal(t);
            LOG.warn("Error during management-context GC: "+t, t);
            // previously we bailed on all errors, but I don't think we should do that -Alex
        }
    }

    public void logUsage(String prefix) {
        if (LOG.isDebugEnabled())
            LOG.debug(prefix+" - using "+getUsageString());
    }

    public static String makeBasicUsageString() {
        return Strings.makeSizeString(Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory())+" / "+
            Strings.makeSizeString(Runtime.getRuntime().totalMemory()) + " memory" +
            " ("+Strings.makeSizeString(MemoryUsageTracker.SOFT_REFERENCES.getBytesUsed()) + " soft); "+
            Thread.activeCount()+" threads";
    }
    
    public String getUsageString() {
        return makeBasicUsageString()+"; "+
            "storage: " + storage.getStorageMetrics() + "; " +
            "tasks: " +
            executionManager.getNumActiveTasks()+" active, "+
            executionManager.getNumIncompleteTasks()+" unfinished; "+
            executionManager.getNumInMemoryTasks()+" remembered, "+
            executionManager.getTotalTasksSubmitted()+" total submitted)";
    }
    
    public void shutdownNow() {
        running = false;
        if (activeCollector != null) activeCollector.cancel(true);
        if (executor != null) executor.shutdownNow();
    }
    
    public void onUnmanaged(Entity entity) {
        // defer task deletions until the entity is completely unmanaged
        // (this is usually invoked during the stop sequence)
        synchronized (unmanagedEntitiesNeedingGc) {
            unmanagedEntitiesNeedingGc.put(entity, Tasks.current());
        }
    }
    
    public void deleteTasksForEntity(Entity entity) {
        // remove all references to this entity from tasks
        executionManager.deleteTag(entity);
        executionManager.deleteTag(BrooklynTaskTags.tagForContextEntity(entity));
        executionManager.deleteTag(BrooklynTaskTags.tagForCallerEntity(entity));
        executionManager.deleteTag(BrooklynTaskTags.tagForTargetEntity(entity));
    }
    
    public void onUnmanaged(Location loc) {
        // No-op currently; no tasks are tracked through their location
    }
    
    public void onTaskDone(Task<?> task) {
        if (shouldDeleteTaskImmediately(task)) {
            executionManager.deleteTask(task);
        }
    }
    
    /** @deprecated since 0.7.0, method moved internal until semantics are clarified; see also {@link #shouldDeleteTaskImmediately(Task)} */
    @Deprecated
    public boolean shouldDeleteTask(Task<?> task) {
        return shouldDeleteTaskImmediately(task);
    }
    /** whether this task should be deleted on completion,
     * because it is transient, or because it is submitted background without much context information */
    protected boolean shouldDeleteTaskImmediately(Task<?> task) {
        if (!task.isDone()) return false;
        
        Set<Object> tags = task.getTags();
        if (tags.contains(ManagementContextInternal.TRANSIENT_TASK_TAG))
            return true;
        if (tags.contains(ManagementContextInternal.EFFECTOR_TAG) || tags.contains(ManagementContextInternal.NON_TRANSIENT_TASK_TAG))
            return false;
        
        if (task.getSubmittedByTask()!=null) {
            Task<?> parent = task.getSubmittedByTask();
            if (executionManager.getTask(parent.getId())==null) {
                // parent is already cleaned up
                return true;
            }
            if (parent instanceof HasTaskChildren && Iterables.contains(((HasTaskChildren)parent).getChildren(), task)) {
                // it is a child, let the parent manage this task's death
                return false;
            }
            Entity associatedEntity = BrooklynTaskTags.getTargetOrContextEntity(task);
            if (associatedEntity!=null) {
                // this is associated to an entity; destroy only if the entity is unmanaged
                return !Entities.isManaged(associatedEntity);
            }
            // if not associated to an entity, then delete immediately
            return true;
        }
        
        // e.g. scheduled tasks, sensor events, etc
        // TODO (in future may keep some of these with another limit, based on a new TagCategory)
        // there may also be a server association for server-side tasks which should be kept
        // (but be careful not to keep too many subscriptions!)
        
        return true;
    }

    /**
     * Deletes old tasks. The age/number of tasks to keep is controlled by fields like 
     * {@link #maxTasksPerTag} and {@link #maxTaskAge}.
     */
    protected synchronized int gcTasks() {
        // TODO Must be careful with memory usage here: have seen OOME if we get crazy lots of tasks.
        // hopefully the use new limits, filters, and use of live lists in some places (added Sep 2014) will help.
        // 
        // An option is for getTasksWithTag(tag) to return an ArrayList rather than a LinkedHashSet. That
        // is a far more memory efficient data structure (e.g. 4 bytes overhead per object rather than 
        // 32 bytes overhead per object for HashSet).
        //
        // More notes on optimization is in the history of this file.
        
        if (!running) return 0;
        
        Duration newPeriod = brooklynProperties.getConfig(GC_PERIOD);
        if (!Objects.equal(gcPeriod, newPeriod)) {
            // caller has changed period, reschedule on next run
            scheduleCollector(false);
        }
    
        expireUnmanagedEntityTasks();
        expireAgedTasks();
        expireTransientTasks();
        
        // now look at overcapacity tags, non-entity tags first
        
        Set<Object> taskTags = executionManager.getTaskTags();
        
        int maxTasksPerEntity = brooklynProperties.getConfig(MAX_TASKS_PER_ENTITY);
        int maxTasksPerTag = brooklynProperties.getConfig(MAX_TASKS_PER_TAG);
        
        Map<Object,AtomicInteger> taskNonEntityTagsOverCapacity = MutableMap.of();
        Map<Object,AtomicInteger> taskEntityTagsOverCapacity = MutableMap.of();
        
        Map<Object,AtomicInteger> taskAllTagsOverCapacity = MutableMap.of();
        
        for (Object tag : taskTags) {
            if (isTagIgnoredForGc(tag)) continue;
            
            Set<Task<?>> tasksWithTag = executionManager.tasksWithTagLiveOrNull(tag);
            if (tasksWithTag==null) continue;
            AtomicInteger overA = null;
            if (tag instanceof WrappedEntity) {
                int over = tasksWithTag.size() - maxTasksPerEntity;
                if (over>0) {
                    overA = new AtomicInteger(over);
                    taskEntityTagsOverCapacity.put(tag, overA);
                }
            } else {
                int over = tasksWithTag.size() - maxTasksPerTag;
                if (over>0) {
                    overA = new AtomicInteger(over);
                    taskNonEntityTagsOverCapacity.put(tag, overA);
                }
            }
            if (overA!=null) {
                taskAllTagsOverCapacity.put(tag, overA);
            }
        }
        
        int deletedCount = 0;
        deletedCount += expireOverCapacityTagsInCategory(taskNonEntityTagsOverCapacity, taskAllTagsOverCapacity, TagCategory.NON_ENTITY_NORMAL, false);
        deletedCount += expireOverCapacityTagsInCategory(taskEntityTagsOverCapacity, taskAllTagsOverCapacity, TagCategory.ENTITY, true);
        deletedCount += expireSubTasksWhoseSubmitterIsExpired();
        
        int deletedGlobally = expireIfOverCapacityGlobally();
        deletedCount += deletedGlobally;
        if (deletedGlobally>0) deletedCount += expireSubTasksWhoseSubmitterIsExpired();
        
        return deletedCount;
    }

    protected static boolean isTagIgnoredForGc(Object tag) {
        if (tag == null) return true;
        if (tag.equals(ManagementContextInternal.EFFECTOR_TAG)) return true;
        if (tag.equals(ManagementContextInternal.SUB_TASK_TAG)) return true;
        if (tag.equals(ManagementContextInternal.NON_TRANSIENT_TASK_TAG)) return true;
        if (tag.equals(ManagementContextInternal.TRANSIENT_TASK_TAG)) return true;
        if (tag instanceof WrappedStream) {
            return true;
        }
        
        return false;
    }
    
    protected void expireUnmanagedEntityTasks() {
        Iterator<Entry<Entity, Task<?>>> ei;
        synchronized (unmanagedEntitiesNeedingGc) {
            ei = MutableSet.copyOf(unmanagedEntitiesNeedingGc.entrySet()).iterator();
        }
        while (ei.hasNext()) {
            Entry<Entity, Task<?>> ee = ei.next();
            if (Entities.isManaged(ee.getKey())) continue;
            if (ee.getValue()!=null && !ee.getValue().isDone()) continue;
            deleteTasksForEntity(ee.getKey());
            synchronized (unmanagedEntitiesNeedingGc) {
                unmanagedEntitiesNeedingGc.remove(ee.getKey());
            }
        }
    }
    
    protected void expireAgedTasks() {
        Duration maxTaskAge = brooklynProperties.getConfig(MAX_TASK_AGE);
        
        Collection<Task<?>> allTasks = executionManager.allTasksLive();
        Collection<Task<?>> tasksToDelete = MutableList.of();

        try {
            for (Task<?> task: allTasks) {
                if (!task.isDone()) continue;
                if (BrooklynTaskTags.isSubTask(task)) continue;

                if (maxTaskAge.isShorterThan(Duration.sinceUtc(task.getEndTimeUtc())))
                    tasksToDelete.add(task);
            }
            
        } catch (ConcurrentModificationException e) {
            // delete what we've found so far
            LOG.debug("Got CME inspecting aged tasks, with "+tasksToDelete.size()+" found for deletion: "+e);
        }
        
        for (Task<?> task: tasksToDelete) {
            executionManager.deleteTask(task);
        }
    }
    
    protected void expireTransientTasks() {
        Set<Task<?>> transientTasks = executionManager.getTasksWithTag(BrooklynTaskTags.TRANSIENT_TASK_TAG);
        for (Task<?> t: transientTasks) {
            if (!t.isDone()) continue;
            executionManager.deleteTask(t);
        }
    }
    
    protected int expireSubTasksWhoseSubmitterIsExpired() {
        // ideally we wouldn't have this; see comments on CHECK_SUBTASK_SUBMITTERS
        if (!brooklynProperties.getConfig(CHECK_SUBTASK_SUBMITTERS))
            return 0;
        
        Collection<Task<?>> allTasks = executionManager.allTasksLive();
        Collection<Task<?>> tasksToDelete = MutableList.of();
        try {
            for (Task<?> task: allTasks) {
                if (!task.isDone()) continue;
                Task<?> submitter = task.getSubmittedByTask();
                // if we've leaked, ie a subtask which is not a child task, 
                // and the submitter is GC'd, then delete this also
                if (submitter!=null && submitter.isDone() && executionManager.getTask(submitter.getId())==null) {
                    tasksToDelete.add(task);
                }
            }
            
        } catch (ConcurrentModificationException e) {
            // delete what we've found so far
            LOG.debug("Got CME inspecting aged tasks, with "+tasksToDelete.size()+" found for deletion: "+e);
        }
        
        for (Task<?> task: tasksToDelete) {
            executionManager.deleteTask(task);
        }
        return tasksToDelete.size();
    }
    
    protected enum TagCategory { 
        ENTITY, NON_ENTITY_NORMAL;
        
        public boolean acceptsTag(Object tag) {
            if (isTagIgnoredForGc(tag)) return false;
            if (tag instanceof WrappedEntity) return this==ENTITY;
            if (this==ENTITY) return false;
            return true;
        }
    } 


    /** expires tasks which are over-capacity in all their non-entity tag categories, returned count */
    protected int expireOverCapacityTagsInCategory(Map<Object, AtomicInteger> taskTagsInCategoryOverCapacity, Map<Object, AtomicInteger> taskAllTagsOverCapacity, TagCategory category, boolean emptyFilterNeeded) {
        if (emptyFilterNeeded) {
            // previous run may have decremented counts  
            MutableList<Object> nowOkayTags = MutableList.of(); 
            for (Map.Entry<Object,AtomicInteger> entry: taskTagsInCategoryOverCapacity.entrySet()) {
                if (entry.getValue().get()<=0) nowOkayTags.add(entry.getKey());
            }
            for (Object tag: nowOkayTags) taskTagsInCategoryOverCapacity.remove(tag);
        }
        
        if (taskTagsInCategoryOverCapacity.isEmpty())
            return 0;
        
        Collection<Task<?>> tasks = executionManager.allTasksLive();
        List<Task<?>> tasksToConsiderDeleting = MutableList.of();
        try {
            for (Task<?> task: tasks) {
                if (!task.isDone()) continue;
                
                Set<Object> tags = task.getTags();

                int categoryTags = 0, tooFullCategoryTags = 0;
                for (Object tag: tags) {
                    if (category.acceptsTag(tag)) {
                        categoryTags++;
                        if (taskTagsInCategoryOverCapacity.containsKey(tag))
                            tooFullCategoryTags++;
                    }
                }
                if (tooFullCategoryTags>0) {
                    if (categoryTags==tooFullCategoryTags) {
                        // all buckets are full, delete this one
                        tasksToConsiderDeleting.add(task);
                    } else {
                        // if any bucket is under capacity, then give grace to the other buckets in this category
                        for (Object tag: tags) {
                            if (category.acceptsTag(tag)) {
                                AtomicInteger over = taskTagsInCategoryOverCapacity.get(tag);
                                if (over!=null) {
                                    if (over.decrementAndGet()<=0) {
                                        // and remove it from over-capacity if so
                                        taskTagsInCategoryOverCapacity.remove(tag);
                                        if (taskTagsInCategoryOverCapacity.isEmpty())
                                            return 0;
                                    }
                                }
                            }
                        }
                    }
                }
            }

        } catch (ConcurrentModificationException e) {
            // do CME's happen with these data structures?
            // if so, let's just delete what we've found so far
            LOG.debug("Got CME inspecting tasks, with "+tasksToConsiderDeleting.size()+" found for deletion: "+e);
        }

        if (LOG.isDebugEnabled())
            LOG.debug("brooklyn-gc detected "+taskTagsInCategoryOverCapacity.size()+" "+category+" "
                    + "tags over capacity, expiring old tasks; "
                    + tasksToConsiderDeleting.size()+" tasks under consideration; categories are: "
                    + taskTagsInCategoryOverCapacity);

        Collections.sort(tasksToConsiderDeleting, TASKS_OLDEST_FIRST_COMPARATOR);
        // now try deleting tasks which are overcapacity for each (non-entity) tag
        int deleted = 0;
        for (Task<?> task: tasksToConsiderDeleting) {
            boolean delete = true;
            for (Object tag: task.getTags()) {
                if (!category.acceptsTag(tag))
                    continue;
                if (taskTagsInCategoryOverCapacity.get(tag)==null) {
                    // no longer over capacity in this tag
                    delete = false;
                    break;
                }
            }
            if (delete) {
                // delete this and update overcapacity info
                deleted++;
                executionManager.deleteTask(task);
                for (Object tag: task.getTags()) {
                    AtomicInteger counter = taskAllTagsOverCapacity.get(tag);
                    if (counter!=null && counter.decrementAndGet()<=0)
                        taskTagsInCategoryOverCapacity.remove(tag);
                }
                if (LOG.isTraceEnabled())
                    LOG.trace("brooklyn-gc deleted "+task+", buckets now "+taskTagsInCategoryOverCapacity);
                if (taskTagsInCategoryOverCapacity.isEmpty())
                    break;
            }
        }

        if (LOG.isDebugEnabled())
            LOG.debug("brooklyn-gc deleted "+deleted+" tasks in over-capacity " + category+" tag categories; "
                    + "capacities now: " + taskTagsInCategoryOverCapacity);
        return deleted;
    }

    protected int expireIfOverCapacityGlobally() {
        Collection<Task<?>> tasksLive = executionManager.allTasksLive();
        if (tasksLive.size() <= brooklynProperties.getConfig(MAX_TASKS_GLOBAL))
            return 0;
        LOG.debug("brooklyn-gc detected "+tasksLive.size()+" tasks in memory, over global limit, looking at deleting some");
        
        try {
            tasksLive = MutableList.copyOf(tasksLive);
        } catch (ConcurrentModificationException e) {
            tasksLive = executionManager.getTasksWithAllTags(MutableList.of());
        }

        MutableList<Task<?>> tasks = MutableList.of();
        for (Task<?> task: tasksLive) {
            if (task.isDone()) {
                tasks.add(task);
            }
        }
        
        int numToDelete = tasks.size() - brooklynProperties.getConfig(MAX_TASKS_GLOBAL);
        if (numToDelete <= 0) {
            LOG.debug("brooklyn-gc detected only "+tasks.size()+" completed tasks in memory, not over global limit, so not deleting any");
            return 0;
        }
            
        Collections.sort(tasks, TASKS_OLDEST_FIRST_COMPARATOR);
        
        int numDeleted = 0;
        while (numDeleted < numToDelete && tasks.size()>numDeleted) {
            executionManager.deleteTask( tasks.get(numDeleted++) );
        }
        if (LOG.isDebugEnabled())
            LOG.debug("brooklyn-gc deleted "+numDeleted+" tasks as was over global limit, now have "+executionManager.allTasksLive().size());
        return numDeleted;
    }

}
