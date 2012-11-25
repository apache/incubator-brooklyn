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
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.management.Task;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.exceptions.RuntimeInterruptedException;
import brooklyn.util.task.BasicExecutionManager;
import brooklyn.util.task.ExecutionListener;

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

    protected static final Logger LOG = LoggerFactory.getLogger(BrooklynGarbageCollector.class);

    public static final ConfigKey<Long> GC_PERIOD = new BasicConfigKey<Long>(
            Long.class, "brooklyn.gc.period", "the period, in millisconds, for checking if any tasks need to be deleted", 60*1000L);
    
    public static final ConfigKey<Integer> MAX_TASKS_PER_TAG = new BasicConfigKey<Integer>(
            Integer.class, "brooklyn.gc.maxTasksPerTag", 
            "the maximum number of tasks to be kept for a given tag (e.g. for effector calls invoked on a particular entity)", 
            100);
    
    public static final ConfigKey<Long> MAX_TASK_AGE = new BasicConfigKey<Long>(
            Long.class, "brooklyn.gc.maxTaskAge", 
            "the number of milliseconds after which a completed task will be automatically deleted", 
            TimeUnit.DAYS.toMillis(1));
    
    private final BasicExecutionManager executionManager;
    private final ScheduledExecutorService executor;
    private final long gcPeriodMs;
    private final int maxTasksPerTag;
    private final long maxTaskAge;
    private volatile boolean running = true;
    
    public BrooklynGarbageCollector(BrooklynProperties brooklynProperties, BasicExecutionManager executionManager){
        this.executionManager = executionManager;

        gcPeriodMs = brooklynProperties.getConfig(GC_PERIOD);
        maxTasksPerTag = brooklynProperties.getConfig(MAX_TASKS_PER_TAG);
        maxTaskAge = brooklynProperties.getConfig(MAX_TASK_AGE);
        
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
                        gc();
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

    public void shutdownNow() {
        running = false;
        if (executor != null) executor.shutdownNow();
    }
    
    public void onUnmanaged(Entity entity) {
        executionManager.deleteTag(entity);
    }
    
    public void onTaskDone(Task<?> task) {
        Set<Object> tags = task.getTags();
        if (tags.contains(AbstractManagementContext.EFFECTOR_TAG) || tags.contains(AbstractManagementContext.NON_TRANSIENT_TASK_TAG)) {
            // keep it for a while
        } else {
            executionManager.deleteTask(task);
        }
    }
    
    private void gc() {
        if (!running) return;
        
        Set<Object> taskTags = executionManager.getTaskTags();
        for (Object tag : taskTags) {
            if (tag == null || tag.equals(AbstractManagementContext.EFFECTOR_TAG)) {
                continue; // there'll be other tags
            }
            Set<Task<?>> tasksWithTag = executionManager.getTasksWithTag(tag);
            int numTasksToDelete = (tasksWithTag.size() - maxTasksPerTag);
            if (numTasksToDelete > 0 || maxTaskAge > 0) {
                List<Task<?>> sortedTasks = Lists.newArrayList(tasksWithTag);
                Collections.sort(sortedTasks, new Comparator<Task<?>>() {
                    @Override public int compare(Task<?> t1, Task<?> t2) {
                        long end1 = t1.isDone() ? t1.getEndTimeUtc() : Long.MAX_VALUE;
                        long end2 = t2.isDone() ? t2.getEndTimeUtc() : Long.MAX_VALUE;
                        return (end1 < end2) ? -1 : ((end1 == end2) ? 0 : 1);
                    }
                });
                if (numTasksToDelete > 0) {
                    for (Task<?> taskToDelete : sortedTasks.subList(0, numTasksToDelete)) {
                        if (!taskToDelete.isDone()) break;
                        executionManager.deleteTask(taskToDelete);
                    }
                }
                if (maxTaskAge > 0) {
                    for (Task<?> taskContender : sortedTasks.subList((numTasksToDelete > 0 ? numTasksToDelete : 0), sortedTasks.size())) {
                        if (taskContender.isDone() && (System.currentTimeMillis() - taskContender.getEndTimeUtc() > maxTaskAge)) {
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
