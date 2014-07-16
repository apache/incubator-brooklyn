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

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.BrooklynProperties;
import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.BrooklynTaskTags;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.internal.storage.BrooklynStorage;
import brooklyn.location.Location;
import brooklyn.management.Task;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.exceptions.RuntimeInterruptedException;
import brooklyn.util.task.BasicExecutionManager;
import brooklyn.util.task.ExecutionListener;
import brooklyn.util.text.Strings;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

/**
 * Deletes record of old tasks, to prevent space leaks and the eating up of more and more memory.
 * 
 * The deletion policy is configurable:
 * <ul>
 *   <li>Period - how frequently to look at the existing tasks to delete some, if required
 *   <li>Max tasks per tag - the maximum number of tasks to be kept for a given tag (e.g. for  
 *       effector calls invoked on a particular entity)
 *   <li>Max task age - the time after which a completed task will be automatically deleted
 *       (i.e. any task completed more than maxTaskAge+period milliseconds ago will definitely 
 *       be deleted.
 * </ul>
 * 
 * The default is to check with a period of one minute, to keep at most 100 tasks per tag, and to
 * delete old completed tasks after one day.
 * 
 * @author aled
 */
public class BrooklynGarbageCollector {

    private static final Logger LOG = LoggerFactory.getLogger(BrooklynGarbageCollector.class);

    public static final ConfigKey<Long> GC_PERIOD = new BasicConfigKey<Long>(
            Long.class, "brooklyn.gc.period", "the period, in millisconds, for checking if any tasks need to be deleted", 60*1000L);
    
    public static final ConfigKey<Boolean> DO_SYSTEM_GC = new BasicConfigKey<Boolean>(
            Boolean.class, "brooklyn.gc.doSystemGc", "whether to periodically call System.gc()", false);
    
    public static final ConfigKey<Integer> MAX_TASKS_PER_TAG = new BasicConfigKey<Integer>(
            Integer.class, "brooklyn.gc.maxTasksPerTag", 
            "the maximum number of tasks to be kept for a given tag (e.g. for effector calls invoked on a particular entity)", 
            100);
    
    public static final ConfigKey<Long> MAX_TASK_AGE = new BasicConfigKey<Long>(
            Long.class, "brooklyn.gc.maxTaskAge", 
            "the number of milliseconds after which a completed task will be automatically deleted", 
            TimeUnit.DAYS.toMillis(1));
    
    private final BasicExecutionManager executionManager;
    private final BrooklynStorage storage;
    private final ScheduledExecutorService executor;
    private final long gcPeriodMs;
    private final int maxTasksPerTag;
    private final long maxTaskAge;
    private final boolean doSystemGc;
    private volatile boolean running = true;

    
    public BrooklynGarbageCollector(BrooklynProperties brooklynProperties, BasicExecutionManager executionManager, BrooklynStorage storage) {
        this.executionManager = executionManager;
        this.storage = storage;

        gcPeriodMs = brooklynProperties.getConfig(GC_PERIOD);
        maxTasksPerTag = brooklynProperties.getConfig(MAX_TASKS_PER_TAG);
        maxTaskAge = brooklynProperties.getConfig(MAX_TASK_AGE);
        doSystemGc = brooklynProperties.getConfig(DO_SYSTEM_GC);
        
        executor = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
                @Override public Thread newThread(Runnable r) {
                    return new Thread(r, "brooklyn-gc");
                }});
        
        executionManager.addListener(new ExecutionListener() {
                @Override public void onTaskDone(Task<?> task) {
                    BrooklynGarbageCollector.this.onTaskDone(task);
                }});
    
        executor.scheduleWithFixedDelay(
            new Runnable() {
                @Override public void run() {
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
                    } catch (RuntimeInterruptedException e) {
                        throw e; // graceful shutdown
                    } catch (Throwable t) {
                        LOG.warn("Error during management-context GC", t);
                        throw Exceptions.propagate(t);
                    }
                }
            }, 
            gcPeriodMs, 
            gcPeriodMs, 
            TimeUnit.MILLISECONDS);
    }

    public void logUsage(String prefix) {
        if (LOG.isDebugEnabled())
            LOG.debug(prefix+" - using "+getUsageString());
    }
    
    public String getUsageString() {
        return
            Strings.makeSizeString(Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory())+" / "+
            Strings.makeSizeString(Runtime.getRuntime().totalMemory()) + " memory; "+
            "storage: " + storage.getStorageMetrics() + "; " +
            "tasks: " +
            executionManager.getNumActiveTasks()+" active, "+
            executionManager.getNumInMemoryTasks()+" in memory "+
            "("+executionManager.getNumIncompleteTasks()+" incomplete and "+
            executionManager.getTotalTasksSubmitted()+" total submitted)";
    }
    
    public void shutdownNow() {
        running = false;
        if (executor != null) executor.shutdownNow();
    }
    
    public void onUnmanaged(Entity entity) {
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
        if (shouldDeleteTask(task)) {
            executionManager.deleteTask(task);
        }
    }
    
    public boolean shouldDeleteTask(Task<?> task) {
        Set<Object> tags = task.getTags();
        if (tags.contains(ManagementContextInternal.TRANSIENT_TASK_TAG))
            return true;
        if (tags.contains(ManagementContextInternal.EFFECTOR_TAG) || tags.contains(ManagementContextInternal.NON_TRANSIENT_TASK_TAG))
            return false;
        if (task.getSubmittedByTask()!=null && !shouldDeleteTask(task.getSubmittedByTask()))
            return false;
        // e.g. scheduled tasks, sensor events, etc
        // TODO (in future may keep these for a shorter duration, e.g. to investigate)
        return true;
    }

    /**
     * Deletes old tasks. The age/number of tasks to keep is controlled by fields like 
     * {@link #maxTasksPerTag} and {@link #maxTaskAge}.
     */
    private void gcTasks() {
        // TODO Must be careful with memory usage here : saw an OOME when copying the tasks set to sort it.
        // (This happened when there were a *lot* of tasks (e.g. 100,000s) due to a crazy trigger for  
        // effector calls.)
        // 
        // At that point we have potentially three copies of the list in-memory:
        //  - original in ExecutionManager
        //  - copy returned by getTasksWithTag(tag)
        //  - copy for sortedTasks (but at least now that only contains done tasks)
        // 
        // It's hard to avoid fewer copies: getTasksWithTag(tag) should continue to return copy rather than 
        // mutable underlying list; and if we're going to sort then we need to take a copy.
        // 
        // An option is for getTasksWithTag(tag) to return an ArrayList rather than a LinkedHashSet. That
        // is a far more memory efficient data structure (e.g. 4 bytes overhead per object rather than 
        // 32 bytes overhead per object for HashSet).
        
        if (!running) return;
        
        Set<Object> taskTags = executionManager.getTaskTags();
        for (Object tag : taskTags) {
            if (tag == null || tag.equals(ManagementContextInternal.EFFECTOR_TAG) 
                    || tag.equals(ManagementContextInternal.SUB_TASK_TAG)
                    || tag.equals(ManagementContextInternal.NON_TRANSIENT_TASK_TAG)
                    || tag.equals(ManagementContextInternal.TRANSIENT_TASK_TAG)) {
                continue; // there'll be other tags
            }
            
            // For each tag, we find the tasks with that tag; we ignore sub-tasks
            // as those will be automatically GC'ed by the parent task at the appropriate point.
            Set<Task<?>> tasksWithTag = executionManager.getTasksWithTag(tag);

            Iterable<Task<?>> topTasksWithTag = Iterables.filter(tasksWithTag, new Predicate<Task<?>>() {
                    @Override public boolean apply(Task<?> input) {
                        return input != null && input.isDone() && !input.getTags().contains(ManagementContextInternal.SUB_TASK_TAG);
                    }});
            
            int numTasksToDelete = (Iterables.size(topTasksWithTag) - maxTasksPerTag);
            if (numTasksToDelete > 0 || maxTaskAge > 0) {
                List<Task<?>> sortedTasks = Lists.newArrayList(topTasksWithTag);
                topTasksWithTag = null;
                tasksWithTag = null;
                
                Collections.sort(sortedTasks, new Comparator<Task<?>>() {
                    @Override public int compare(Task<?> t1, Task<?> t2) {
                        // know that tasks are all done; filtered those above
                        long end1 = t1.getEndTimeUtc();
                        long end2 = t2.getEndTimeUtc();
                        return (end1 < end2) ? -1 : ((end1 == end2) ? 0 : 1);
                    }
                });
                if (numTasksToDelete > 0) {
                    for (Task<?> taskToDelete : sortedTasks.subList(0, numTasksToDelete)) {
                        executionManager.deleteTask(taskToDelete);
                    }
                }
                if (maxTaskAge > 0) {
                    for (Task<?> taskContender : sortedTasks.subList((numTasksToDelete > 0 ? numTasksToDelete : 0), sortedTasks.size())) {
                        if (System.currentTimeMillis() - taskContender.getEndTimeUtc() > maxTaskAge) {
                            executionManager.deleteTask(taskContender);
                        } else {
                            break; // all subsequent tasks will be newer; stop looking
                        }
                    }
                }
            }
        }
    }
}
