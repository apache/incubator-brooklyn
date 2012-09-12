package brooklyn.util.task;

import groovy.lang.Closure;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import brooklyn.management.ExecutionContext;
import brooklyn.management.Task;
import brooklyn.util.flags.TypeCoercions;

import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class Tasks {

    /** convenience for setting "blocking details" on any task where the current thread is running;
     * typically invoked prior to a wait, for transparency to a user;
     * then invoked with 'null' just after the wait */
    public static void setBlockingDetails(String description) {
        try {
            withBlockingDetails(description, null);
        } catch (Exception e) {
            Throwables.propagate(e);
        }
    }
    
    /** convenience for setting "blocking details" on any task where the current thread is running,
     * while the passed code is executed; often used from groovy as
     * <code> withBlockingDetails("sleeping 5s") { Thread.sleep(5000); } </code>.
     * if code block is null, the description is set until further notice (not cleareed). */
    @SuppressWarnings("rawtypes")
    public static Object withBlockingDetails(String description, Callable code) throws Exception {
        Task current = current();
        if (current instanceof BasicTask)
            ((BasicTask)current).setBlockingDetails(description); 
        if (code!=null) {
            try {
                return code.call();
            } finally {
                if (current instanceof BasicTask)
                    ((BasicTask)current).setBlockingDetails(null); 
            }
        }
        return null;
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
        //but not allowed to return a future as the resolved value
        if (v==null || (type.isInstance(v) && !Future.class.isInstance(v)))
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
            } else if (v instanceof Closure) {
                v = ((Closure) v).call();
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
        Task current = current();
        if (current instanceof BasicTask)
            ((BasicTask)current).setExtraStatusText(notes); 
    }

}
