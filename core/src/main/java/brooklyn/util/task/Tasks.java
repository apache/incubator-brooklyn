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

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class Tasks {

    /** attempt to resolve the given value as the given type, waiting on futures, submitting if necessary,
     * and coercing as allowed by TypeCoercions */
    public static <T> T resolveValue(Object v, Class<T> type, ExecutionContext exec) throws ExecutionException, InterruptedException {
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
                    
                    BasicExecutionManager.withBlockingDetails("waiting for "+v, new Callable<Void>() {
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
                    result.put(entry.getKey(), resolveValue(entry.getValue(), type, exec));
                }
                return (T) result;
            } else if (v instanceof List) {
                List result = Lists.newArrayList();
                for (Object it : (List)v) {
                    result.add(resolveValue(it, type, exec));
                }
                return (T) result;
            } else {
                return TypeCoercions.coerce(v, type);
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Error resolving "+v+" in "+exec+": "+e, e);
        }
        return resolveValue(v, type, exec);
    }

}
