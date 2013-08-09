package brooklyn.util.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.management.Task;
import brooklyn.management.TaskQueueingContext;

import com.google.common.annotations.Beta;
import com.google.common.base.Preconditions;

/** contains static methods which detect and use the current {@link TaskQueueingContext} to execute tasks */
@Beta // introduced in 0.6.0
public class DynamicTasks {

    private static final Logger log = LoggerFactory.getLogger(DynamicTasks.class);
    private static final ThreadLocal<TaskQueueingContext> taskQueueingContext = new ThreadLocal<TaskQueueingContext>();
    
    public static void setTaskQueueingContext(TaskQueueingContext newTaskQC) {
        taskQueueingContext.set(newTaskQC);
    }
    
    public static TaskQueueingContext getTaskQueuingContext() {
        return taskQueueingContext.get();
    }
    
    public static void removeTaskQueueingContext() {
        taskQueueingContext.remove();
    }

    /** tries to add the task to the current addition context if there is one, otherwise does nothing */
    public static <T> Task<T> queueIfPossible(Task<T> task) {
        TaskQueueingContext adder = getTaskQueuingContext();
        if (adder!=null)
            Tasks.tryQueueing(adder, task);
        return task;
    }
    
    /** adds the given task to the nearest task addition context,
     * either set as a thread-local, or in the current task, or the submitter of the task, etc
     * <p>
     * throws if it cannot add */
    public static <T> Task<T> queueInTaskHierarchy(Task<T> task) {
        Preconditions.checkNotNull(task, "Task to queue cannot be null");
        Preconditions.checkState(!Tasks.isQueuedOrSubmitted(task), "Task to queue must not yet be submitted: {}", task);
        
        TaskQueueingContext adder = getTaskQueuingContext();
        if (adder!=null) { 
            if (Tasks.tryQueueing(adder, task)) {
                log.debug("Queued task {} at context {} (no hierarchy)", task, adder);
                return task;
            }
        }
        
        Task<?> t = Tasks.current();
        Preconditions.checkState(t!=null || adder!=null, "No task addition context available for queueing task "+task);
        
        while (t!=null) {
            if (t instanceof TaskQueueingContext) {
                if (Tasks.tryQueueing((TaskQueueingContext)t, task)) {
                    log.debug("Queued task {} at hierarchical context {}", task, t);
                    return task;
                }
            }
            t = t.getSubmittedByTask();
        }
        
        throw new IllegalStateException("No task addition context available in current task hierarchy for adding task "+task);
    }

    public static <U,V extends Task<U>> V queue(V task) {
        Preconditions.checkNotNull(task, "Task to queue cannot be null");
        Preconditions.checkState(!Tasks.isQueuedOrSubmitted(task), "Task to queue must not yet be submitted: %s", task);
        TaskQueueingContext adder = getTaskQueuingContext();
        if (adder==null) {
            Task<?> t = Tasks.current();
            if (t instanceof TaskQueueingContext) adder = (TaskQueueingContext) t;
        }
        Preconditions.checkNotNull(adder, "Task %s cannot be queued here; no queueing context available", task);
        adder.queue(task);
        return task;
    }

    public static <T> Task<T> queueIfNeeded(Task<T> task) {
        if (!Tasks.isQueuedOrSubmitted(task))
            queue(task);
        return task;
    }
    
    /** submits the given task if needed, and gets the result (unchecked) 
     * only permitted in a queueing context (ie a DST main job) */
    // things get really confusing if you try to queueInTaskHierarchy -- easy to cause deadlocks!
    public static <T> T get(Task<T> t) {
        return queueIfNeeded(t).getUnchecked();
    }
    
}
