package brooklyn.util.task;

import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.management.Task;
import brooklyn.management.TaskQueueingContext;
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

    public abstract static class AutoQueue<T> implements Supplier<T> {
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
    }

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

    // TODO use or remove very soon!
//    /** creates a TaskQueueingContext inside the given task if necessary, returning whether it was necessary
//     * <p>
//     * for use in legacy tasks where we want to use the new queueing features
//     * <p>
//     * in some cases the default behaviour may be to block on any queued task, 
//     * as subsequent activities might assume the task has completed, depending on the code being migrated
//     * <p>
//     * @deprecated since 0.6.0-m1 intended for migration purposes
//     */
//    @Beta // for use in migration only
//    @Deprecated
//    public static boolean installTaskQueueingContextForBasicTask(final boolean blocking) {
//        if (getTaskQueuingContext()!=null) return false;
//        // TODO clear the task queueing context when the task ends!!!
//        // if that's even possible ????!!?!?
//        setTaskQueueingContext(new TaskQueueingContext() {
//            List<Task<?>> list = new ArrayList<Task<?>>();
//            @Override
//            public void queue(Task<?> t) {
//                Preconditions.checkState(!Tasks.isQueuedOrSubmitted(t), "Task %s is already queued or submitted; cannot queue.", t);
//                Preconditions.checkNotNull(Tasks.current(), "Can only use a TaskQueueingContext inside a task; cannot queue %s here.", t);
//                if (BasicExecutionContext.getCurrentExecutionContext() == null) {
//                    ExecutionManager em = ((BasicTask<?>)Tasks.current()).em;
//                    if (em!=null) {
//                        log.warn("Discouraged submission of compound task ({}) from {} without execution context; using execution manager", t, Tasks.current());
//                        em.submit(t);
//                    } else {
//                        throw new IllegalStateException("Cannot queue task ("+t+") inside "+Tasks.current()+" as there is no task context");
//                    }
//                } else {
//                    BasicExecutionContext.getCurrentExecutionContext().submit(t);
//                }
//                if (blocking) t.blockUntilEnded();
//            }
//            
//            @Override
//            public Task<?> last() {
//                if (list.isEmpty()) return null;
//                return list.get(list.size()-1);
//            }
//            
//            @Override
//            public List<Task<?>> getQueue() {
//                return ImmutableList.copyOf(list);
//            }
//        });
//        return true;
//    }

}
