package brooklyn.util.task;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

import brooklyn.management.Task;
import brooklyn.management.TaskFactory;
import brooklyn.management.TaskQueueingContext;
import brooklyn.util.JavaGroovyEquivalents;
import brooklyn.util.collections.MutableMap;

/** Convenience for creating tasks; note that DynamicSequentialTask is the default */
public class TaskBuilder<T> {

    String name = null;
    Callable<T> body = null;
    List<Task<?>> children = new ArrayList<Task<?>>();
    Set<Object> tags = new LinkedHashSet<Object>();
    Boolean dynamic = null;
    boolean parallel = false;
    
    public static <T> TaskBuilder<T> builder() {
        return new TaskBuilder<T>();
    }
    
    public TaskBuilder<T> name(String name) {
        this.name = name;
        return this;
    }
    
    /** whether task that is built has been explicitly specified to be a dynamic task 
     * (ie a Task which is also a {@link TaskQueueingContext}
     * whereby new tasks can be added after creation */
    public TaskBuilder<T> dynamic(boolean dynamic) {
        this.dynamic = dynamic;
        return this;
    }
    
    /** whether task that is built should be parallel; cannot (currently) also be dynamic */
    public TaskBuilder<T> parallel(boolean parallel) {
        this.parallel = parallel;
        return this;
    }
    
    public TaskBuilder<T> body(Callable<T> body) {
        this.body = body;
        return this;
    }
    
    public TaskBuilder<T> body(Runnable body) {
        this.body = JavaGroovyEquivalents.<T>toCallable(body);
        return this;
    }

    /** adds a child to the given task; the semantics of how the child is executed is set using
     * {@link #dynamic(boolean)} and {@link #parallel(boolean)} */
    public TaskBuilder<T> add(Task<?> child) {
        children.add(child);
        return this;
    }

    /** adds a tag to the given task */
    public TaskBuilder<T> tag(Object tag) {
        tags.add(tag);
        return this;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public Task<T> build() {
        MutableMap<String, Object> flags = MutableMap.of();
        if (name!=null) flags.add("displayName", name);
        if (!tags.isEmpty()) flags.add("tags", tags);
        
        if (dynamic==Boolean.FALSE && children.isEmpty())
            return new BasicTask<T>(flags, body);
        
        // prefer dynamic set unless (a) user has said not dynamic, or (b) it's parallel (since there is no dynamic parallel yet)
        // dynamic has better cancel (will interrupt the thread) and callers can submit tasks flexibly;
        // however dynamic uses an extra thread and task and is noisy for contexts which don't need it
        if (dynamic==Boolean.TRUE || (dynamic==null && !parallel)) {
            if (parallel)
                throw new UnsupportedOperationException("No implementation of parallel dynamic aggregate task available");
            DynamicSequentialTask<T> result = new DynamicSequentialTask<T>(flags, body);
            for (Task t: children)
                result.queue(t);
            return result;
        }
        
        // T must be of type List<V> for these to be valid
        if (body != null) {
            throw new UnsupportedOperationException("No implementation of non-dynamic task with both body and children");
        }
        
        if (parallel)
            return new ParallelTask(flags, children);
        else
            return new SequentialTask(flags, children);
    }

    /** returns a a factory based on this builder */
    public TaskFactory<Task<T>> buildFactory() {
        return new TaskFactory<Task<T>>() {
            public Task<T> newTask() {
                return build();
            }
        };
    }
    
    @Override
    public String toString() {
        return super.toString()+"["+name+"]";
    }
}
