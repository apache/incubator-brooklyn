package brooklyn.util.task;

import groovy.lang.Closure;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.management.HasTaskChildren;
import brooklyn.management.Task;
import brooklyn.util.collections.MutableMap;

/** Represents a task whose run() method can create other tasks
 * which are run sequentially, but that sequence runs in parallel to this task 
 **/
public class DynamicSequentialTask<T> extends BasicTask<T> implements HasTaskChildren {

    private static final Logger log = LoggerFactory.getLogger(CompoundTask.class);
                
    protected final Queue<Task> children = new ConcurrentLinkedQueue();
    protected T result;
    
    /**
     * Constructs a new compound task containing the specified units of work.
     * 
     * @param jobs  A potentially heterogeneous mixture of {@link Runnable}, {@link Callable}, {@link Closure} and {@link Task} can be provided. 
     * @throws IllegalArgumentException if any of the passed child jobs is not one of the above types 
     */
    public DynamicSequentialTask() {
        super(MutableMap.of("tag", "compound"));
    }
    
    public DynamicSequentialTask(Map<String,?> flags) {
        super(flags);
        
//        this.result = new ArrayList<Object>(jobs.size());
//        this.children = new ArrayList<Task<? extends T>>(jobs.size());
//        for (Object job : jobs) {
//            if (job instanceof Task)          { children.add((Task) job); }
//            else if (job instanceof Closure)  { children.add(new BasicTask((Closure) job)); }
//            else if (job instanceof Callable) { children.add(new BasicTask((Callable) job)); }
//            else if (job instanceof Runnable) { children.add(new BasicTask((Runnable) job)); }
//            
//            else throw new IllegalArgumentException("Invalid child "+job+
//                " passed to compound task; must be Runnable, Callable, Closure or Task");
//        }
    }
    
    @SuppressWarnings("deprecation")
    protected void submitIfNecessary(Task<?> task) {
        if (!task.isSubmitted()) {
            if (BasicExecutionContext.getCurrentExecutionContext() == null) {
                if (em!=null) {
                    log.warn("Discouraged submission of compound task ({}) from {} without execution context; using execution manager", task, this);
                    em.submit(task);
                } else {
                    throw new IllegalStateException("Compound task ("+task+") launched from "+this+" missing required execution context");
                }
            } else {
                BasicExecutionContext.getCurrentExecutionContext().submit(task);
            }
        }
    }
    
    public Iterable<Task> getChildrenTasks() {
        return children;
    }
    
}
