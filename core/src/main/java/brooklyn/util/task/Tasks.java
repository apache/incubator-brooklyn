package brooklyn.util.task;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.management.ExecutionContext;
import brooklyn.management.Task;
import brooklyn.management.TaskQueueingContext;
import brooklyn.util.flags.TypeCoercions;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class Tasks {

    
    private static final Logger log = LoggerFactory.getLogger(Tasks.class);
    
    /** convenience for setting "blocking details" on any task where the current thread is running;
     * typically invoked prior to a wait, for transparency to a user;
     * then invoked with 'null' just after the wait */
    public static void setBlockingDetails(String description) {
        Task<?> current = current();
        if (current instanceof BasicTask)
            ((BasicTask<?>)current).setBlockingDetails(description); 
    }
    public static void resetBlockingDetails() {
        Task<?> current = current();
        if (current instanceof BasicTask)
            ((BasicTask<?>)current).resetBlockingDetails(); 
    }
    public static void setBlockingTask(Task<?> blocker) {
        Task<?> current = current();
        if (current instanceof BasicTask)
            ((BasicTask<?>)current).setBlockingTask(blocker); 
    }
    public static void resetBlockingTask() {
        Task<?> current = current();
        if (current instanceof BasicTask)
            ((BasicTask<?>)current).resetBlockingTask(); 
    }
    
    /** convenience for setting "blocking details" on any task where the current thread is running,
     * while the passed code is executed; often used from groovy as
     * <pre>{@code withBlockingDetails("sleeping 5s") { Thread.sleep(5000); } }</pre>
     * If code block is null, the description is set until further notice (not cleareed). */
    @SuppressWarnings("rawtypes")
    public static <T> T withBlockingDetails(String description, Callable<T> code) throws Exception {
        Task current = current();
        if (code==null) {
            log.warn("legacy invocation of withBlockingDetails with null code block, ignoring");
            return null;
        }
        if (current instanceof BasicTask)
            ((BasicTask)current).setBlockingDetails(description); 
        try {
            return code.call();
        } finally {
            if (current instanceof BasicTask)
                ((BasicTask)current).setBlockingDetails(null); 
        }
    }

    /** the {@link Task} where the current thread is executing, if executing in a Task, otherwise null */
    @SuppressWarnings("rawtypes")
    public static Task current() { return BasicExecutionManager.getPerThreadCurrentTask().get(); }

    /** @see #resolveValue(Object, Class, ExecutionContext, String) */
    public static <T> T resolveValue(Object v, Class<T> type, ExecutionContext exec) throws ExecutionException, InterruptedException {
        return resolveValue(v, type, exec, null);
    }
    
    /** attempt to resolve the given value as the given type, waiting on futures, submitting if necessary,
     * and coercing as allowed by TypeCoercions;
     * contextMessage (optional) will be displayed in status reports while it waits (e.g. the name of the config key being looked up) */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static <T> T resolveValue(Object v, Class<T> type, ExecutionContext exec, String contextMessage) throws ExecutionException, InterruptedException {
        //if the expected type is a closure or map and that's what we have, we're done (or if it's null);
        //but not allowed to return a future or DeferredSupplier as the resolved value
        if (v==null || (type.isInstance(v) && !Future.class.isInstance(v) && !DeferredSupplier.class.isInstance(v)))
            return (T) v;
        try {
            //if it's a task or a future, we wait for the task to complete
            if (v instanceof Task) {
                //if it's a task, we make sure it is submitted
                //(perhaps could run it here? ... tbd)
                if (!((Task) v).isSubmitted() ) {
                    exec.submit((Task) v);
                }
            }
            
            if (v instanceof Future) {
                final Future<?> vfuture = (Future<?>) v;
                
                //including tasks, above
                if (!vfuture.isDone()) {
                    final AtomicReference<Object> vref = new AtomicReference<Object>(v);
                    
                    withBlockingDetails("Waiting for "+(contextMessage!=null ? contextMessage+", " : "")+v, 
                            new Callable<Void>() {
                        public Void call() throws Exception {
                            vref.set( vfuture.get() );
                            return null;
                        }
                    });
                    
                    v = vref.get();
                    
                } else {
                    v = vfuture.get();
                }
                
            } else if (v instanceof DeferredSupplier<?>) {
                v = ((DeferredSupplier<?>) v).get();
                
            } else if (v instanceof Map) {
                //and if a map or list we look inside
                Map result = Maps.newLinkedHashMap();
                for (Map.Entry<?,?> entry : ((Map<?,?>)v).entrySet()) {
                    result.put(entry.getKey(), resolveValue(entry.getValue(), type, exec,
                            (contextMessage!=null ? contextMessage+", " : "") + "map entry "+entry.getKey()));
                }
                return (T) result;
                
            } else if (v instanceof List) {
                List result = Lists.newArrayList();
                int count=0;
                for (Object it : (List)v) {
                    result.add(resolveValue(it, type, exec, 
                            (contextMessage!=null ? contextMessage+", " : "") + "list entry "+count));
                    count++;
                }
                return (T) result;
                
            } else {
                return TypeCoercions.coerce(v, type);
            }
            
        } catch (Exception e) {
            throw new IllegalArgumentException("Error resolving "+(contextMessage!=null ? contextMessage+", " : "")+v+", in "+exec+": "+e, e);
        }
        return resolveValue(v, type, exec, contextMessage);
    }

    /** sets extra status details on the current task, if possible (otherwise does nothing).
     * the extra status is presented in Task.getStatusDetails(true)
     */
    public static void setExtraStatusDetails(String notes) {
        Task<?> current = current();
        if (current instanceof BasicTask)
            ((BasicTask<?>)current).setExtraStatusText(notes); 
    }

    public static <T> TaskBuilder<T> builder() {
        return TaskBuilder.<T>builder();
    }
    
    /** returns the first tag found on the given task which matches the given type, looking up the submission hierarachy if necessary */
    @SuppressWarnings("unchecked")
    public static <T> T tag(@Nullable Task<?> task, Class<T> type, boolean recurseHierarchy) {
        // support null task to make it easier for callers to walk hierarchies
        if (task==null) return null;
        for (Object tag: task.getTags())
            if (type.isInstance(tag)) return (T)tag;
        if (!recurseHierarchy) return null;
        return tag(task.getSubmittedByTask(), type, true);
    }
    
    public static boolean isAncestorCancelled(Task<?> t) {
        if (t==null) return false;
        if (t.isCancelled()) return true;
        return isAncestorCancelled(t.getSubmittedByTask());
    }

    public static boolean isQueuedOrSubmitted(Task<?> task) {
        return ((BasicTask<?>)task).isQueuedOrSubmitted();
    }
    
    /** tries to add the given task in the given addition context,
     * returns true if it could, false if it could not (doesn't throw anything) */
    public static boolean tryQueueing(TaskQueueingContext adder, Task<?> task) {
        if (task==null || isQueuedOrSubmitted(task))
            return false;
        try {
            adder.queue(task);
            return true;
        } catch (Exception e) {
            if (log.isDebugEnabled())
                log.debug("Could not add task "+task+" at "+adder+": "+e);
            return false;
        }        
    }
    

}
