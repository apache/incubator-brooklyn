package brooklyn.util.task;

import java.util.List;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.management.Task;
import brooklyn.management.TaskAdaptable;
import brooklyn.management.TaskFactory;
import brooklyn.management.TaskQueueingContext;
import brooklyn.management.TaskWrapper;
import brooklyn.util.exceptions.Exceptions;

import com.google.common.annotations.Beta;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;

/** contains static methods which detect and use the current {@link TaskQueueingContext} to execute tasks */
@Beta // introduced in 0.6.0
public class DynamicTasks {

    private static final Logger log = LoggerFactory.getLogger(DynamicTasks.class);
    private static final ThreadLocal<TaskQueueingContext> taskQueueingContext = new ThreadLocal<TaskQueueingContext>();
    
    public static void setTaskQueueingContext(TaskQueueingContext newTaskQC) {
        taskQueueingContext.set(newTaskQC);
    }
    
    public static TaskQueueingContext getThreadTaskQueuingContext() {
        return taskQueueingContext.get();
    }
    
    public static TaskQueueingContext getTaskQueuingContext() {
        TaskQueueingContext adder = getThreadTaskQueuingContext();
        if (adder!=null) return adder;
        Task<?> t = Tasks.current();
        if (t instanceof TaskQueueingContext) return (TaskQueueingContext) t;
        return null;
    }

    
    public static void removeTaskQueueingContext() {
        taskQueueingContext.remove();
    }

    public static class TaskQueueingResult<T> implements TaskWrapper<T> {
        private final Task<T> task;
        private final boolean wasQueued;
        
        private TaskQueueingResult(TaskAdaptable<T> task, boolean wasQueued) {
            this.task = task.asTask();
            this.wasQueued = wasQueued;
        }
        @Override
        public Task<T> asTask() {
            return task;
        }
        @Override
        public Task<T> getTask() {
            return task;
        }
        /** returns true if the task was queued */
        public boolean wasQueued() {
            return wasQueued;
        }
        /** returns true if the task either is currently queued or has been submitted */
        public boolean isQueuedOrSubmitted() {
            return wasQueued || Tasks.isQueuedOrSubmitted(task);
        }
        private boolean orSubmitInternal() {
            if (!wasQueued()) {
                if (isQueuedOrSubmitted()) {
                    log.warn("Redundant call to execute "+getTask()+"; skipping");
                    return false;
                } else {
                    BasicExecutionContext ec = BasicExecutionContext.getCurrentExecutionContext();
                    if (ec==null)
                        throw new IllegalStateException("Cannot execute "+getTask()+" without an execution context; ensure caller is in an ExecutionContext");
                    ec.submit(getTask());
                    return true;
                }
            } else {
                return false;
            }
        }
        /** causes the task to be submitted (asynchronously) if it hasn't already been */
        public TaskQueueingResult<T> orSubmitAsync() {
            orSubmitInternal();
            return this;
        }
        /** causes the task to be submitted *synchronously* if it hasn't already been submitted;
         * useful in contexts such as libraries where callers may be either on a legacy call path 
         * (which assumes all commands complete immediately)
         *  */
        public TaskQueueingResult<T> orSubmitAndBlock() {
            if (orSubmitInternal()) task.getUnchecked();
            return this;
        }
        /** blocks for the task to be completed
         * <p>
         * needed in any context where subsequent commands assume the task has completed.
         * not needed in a context where the task is simply being built up and queued.
         * <p>
         * throws if there are any errors
         */
        public void andWaitForSuccess() {
            task.getUnchecked();
        }
    }
    
    /** tries to add the task to the current addition context if there is one, otherwise does nothing */
    public static <T> TaskQueueingResult<T> queueIfPossible(TaskAdaptable<T> task) {
        TaskQueueingContext adder = getTaskQueuingContext();
        boolean result = false;
        if (adder!=null)
            result = Tasks.tryQueueing(adder, task);
        return new TaskQueueingResult<T>(task, result);
    }
    public static <T> TaskQueueingResult<T> queueIfPossible(TaskFactory<? extends TaskAdaptable<T>> task) {
        return queueIfPossible(task.newTask());
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

    public static <V extends TaskAdaptable<?>> V queue(V task) {
        try {
            Preconditions.checkNotNull(task, "Task to queue cannot be null");
            Preconditions.checkState(!Tasks.isQueuedOrSubmitted(task), "Task to queue must not yet be submitted: %s", task);
            TaskQueueingContext adder = getTaskQueuingContext();
            Preconditions.checkNotNull(adder, "Task %s cannot be queued here; no queueing context available", task);
            adder.queue(task.asTask());
            return task;
        } catch (Throwable e) {
            log.warn("Error queueing "+task+" (rethrowing): "+e);
            throw Exceptions.propagate(e);
        }
    }

    public static void queue(TaskAdaptable<?> task1, TaskAdaptable<?> task2, TaskAdaptable<?> ...tasks) {
        queue(task1);
        queue(task2);
        for (TaskAdaptable<?> task: tasks) queue(task);
    }

    public static <T extends TaskAdaptable<?>> T queue(TaskFactory<T> taskFactory) {
        return queue(taskFactory.newTask());
    }

    public static void queue(TaskFactory<?> task1, TaskFactory<?> task2, TaskFactory<?> ...tasks) {
        queue(task1.newTask());
        queue(task2.newTask());
        for (TaskFactory<?> task: tasks) queue(task.newTask());
    }
    
    public static <T extends TaskAdaptable<?>> T queueIfNeeded(T task) {
        if (!Tasks.isQueuedOrSubmitted(task))
            queue(task);
        return task;
    }
    
    /** submits the given task if needed, and gets the result (unchecked) 
     * only permitted in a queueing context (ie a DST main job) */
    // things get really confusing if you try to queueInTaskHierarchy -- easy to cause deadlocks!
    public static <T> T get(TaskAdaptable<T> t) {
        return queueIfNeeded(t).asTask().getUnchecked();
    }

    /** convenience for writing code which should be automatically queued as the body of a {@link DynamicSequentialTask},
     * where further tasks can be queued
     * <p>
     * to use, create the class and implement main */
    public abstract static class AutoQueue<T> implements Supplier<T>, TaskWrapper<T> {
        final Task<T> task;
        public AutoQueue(final String name) {
            task = DynamicTasks.queue(Tasks.<T>builder().name(name).body(
                    new Callable<T>() {
                        public T call() throws Exception {
                            return main();
                        }
                    }).build());
        }
        protected abstract T main() throws Exception;
        public T get() {
            try {
                return task.get();
            } catch (Exception e) { throw Exceptions.propagate(e); }
        }
        public Task<T> getTask() {
            return task;
        }
        @Override
        public Task<T> asTask() {
            return getTask();
        }
    }

    /** see {@link AutoQueue} */
    public abstract static class AutoQueueVoid {
        final Task<Void> task;
        public AutoQueueVoid(final String name) {
            task = DynamicTasks.queue(Tasks.<Void>builder().name(name).body(
                    new Callable<Void>() {
                        public Void call() throws Exception {
                            main();
                            return null;
                        }
                    }).build());
        }
        protected abstract void main() throws Exception;
        public void get() {
            try {
                task.get();
            } catch (Exception e) { throw Exceptions.propagate(e); }
        }
    }

    /** Waits for the last task queued in this context to complete;
     * it does throw if there is a problem.
     * <p>
     * Preferred over {@link #last()}.get() because this waits on all tasks, 
     * in sequentially (so that blocking information is always accurate) */
    public static Task<?> waitForLast() {
        TaskQueueingContext qc = DynamicTasks.getTaskQueuingContext();
        Preconditions.checkNotNull(qc, "Cannot wait when their is no queueing context");
        List<Task<?>> q = qc.getQueue();
        Task<?> last = null;
        do {
            for (Task<?> t: q) {
                last = t;
                last.getUnchecked();
            }
        } while (last!=qc.last());
        return last;
    }

}
