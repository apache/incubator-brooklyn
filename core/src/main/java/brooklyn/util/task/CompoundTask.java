package brooklyn.util.task;

import groovy.lang.Closure;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.management.HasTaskChildren;
import brooklyn.management.Task;
import brooklyn.util.collections.MutableMap;


/**
 * A {@link Task} that is comprised of other units of work: possibly a heterogeneous mix of {@link Task},
 * {@link Runnable}, {@link Callable} and {@link Closure} instances.
 * 
 * This class holds the collection of child tasks, but subclasses have the responsibility of executing them in a
 * sensible manner by implementing the abstract {@link #runJobs} method.
 */
public abstract class CompoundTask<T> extends BasicTask<List<T>> implements HasTaskChildren<T> {

    private static final Logger log = LoggerFactory.getLogger(CompoundTask.class);
                
    protected final List<Task<? extends T>> children;
    protected final List<Object> result;
    
    /**
     * Constructs a new compound task containing the specified units of work.
     * 
     * @param jobs  A potentially heterogeneous mixture of {@link Runnable}, {@link Callable}, {@link Closure} and {@link Task} can be provided. 
     * @throws IllegalArgumentException if any of the passed child jobs is not one of the above types 
     */
    public CompoundTask(Object... jobs) {
        this( Arrays.asList(jobs) );
    }
    
    /**
     * Constructs a new compound task containing the specified units of work.
     * 
     * @param jobs  A potentially heterogeneous mixture of {@link Runnable}, {@link Callable}, {@link Closure} and {@link Task} can be provided. 
     * @throws IllegalArgumentException if any of the passed child jobs is not one of the above types 
     */
    public CompoundTask(Collection<?> jobs) {
        this(MutableMap.of("tag", "compound"), jobs);
    }
    
    public CompoundTask(Map<String,?> flags, Collection<?> jobs) {
        super(flags);
        super.job = new Callable<List<T>>() {
            @Override public List<T> call() throws Exception {
                return runJobs();
            }
        };
        
        this.result = new ArrayList<Object>(jobs.size());
        this.children = new ArrayList<Task<? extends T>>(jobs.size());
        for (Object job : jobs) {
            if (job instanceof Task)          { children.add((Task) job); }
            else if (job instanceof Closure)  { children.add(new BasicTask((Closure) job)); }
            else if (job instanceof Callable) { children.add(new BasicTask((Callable) job)); }
            else if (job instanceof Runnable) { children.add(new BasicTask((Runnable) job)); }
            
            else throw new IllegalArgumentException("Invalid child "+job+
                " passed to compound task; must be Runnable, Callable, Closure or Task");
        }
    }

    /** return value needs to be specified by subclass; subclass should also setBlockingDetails 
     * @throws ExecutionException 
     * @throws InterruptedException */    
    protected abstract List<T> runJobs() throws InterruptedException, ExecutionException;
    
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
    
    public List<Task<? extends T>> getChildrenTasks() {
        return children;
    }
    
}
