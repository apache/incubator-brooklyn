package brooklyn.util.task

import java.util.concurrent.Callable

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.management.Task


/**
 * A {@link Task} that is comprised of other units of work: possibly a heterogeneous mix of {@link Task},
 * {@link Runnable}, {@link Callable} and {@link Closure} instances.
 * 
 * This class holds the collection of child tasks, but subclasses have the responsibility of executing them in a
 * sensible manner by implementing the abstract {@link #runJobs} method.
 */
public abstract class CompoundTask extends BasicTask<Object> {

    private static final Logger log = LoggerFactory.getLogger(CompoundTask.class);
                
    protected final Collection<Task> children;
    protected final Collection<Object> result;
    
    /**
     * Constructs a new compound task containing the specified units of work.
     * 
     * @param jobs  A potentially heterogeneous mixture of {@link Runnable}, {@link Callable}, {@link Closure} and {@link Task} can be provided. 
     * @throws IllegalArgumentException if any of the passed child jobs is not one of the above types 
     */
    public CompoundTask(Object... jobs) {
        this( Arrays.asList(jobs) )
    }
    
    /**
     * Constructs a new compound task containing the specified units of work.
     * 
     * @param jobs  A potentially heterogeneous mixture of {@link Runnable}, {@link Callable}, {@link Closure} and {@link Task} can be provided. 
     * @throws IllegalArgumentException if any of the passed child jobs is not one of the above types 
     */
    public CompoundTask(Collection<?> jobs) {
        super( tag:"compound" );
        super.job = { runJobs() }
        
        this.result = new ArrayList<Object>(jobs.size());
        this.children = new ArrayList<Task>(jobs.size());
        jobs.each { job ->
            if (job instanceof Task)          { children.add((Task) job) }
            else if (job instanceof Closure)  { children.add(new BasicTask((Closure) job)) }
            else if (job instanceof Callable) { children.add(new BasicTask((Callable) job)) }
            else if (job instanceof Runnable) { children.add(new BasicTask((Runnable) job)) }
            
            else throw new IllegalArgumentException("Invalid child "+job+
                " passed to compound task; must be Runnable, Callable, Closure or Task");
        }
    }

    /** return value needs to be specified by subclass */    
    protected abstract Object runJobs()
    
    protected void submitIfNecessary(Task task) {
        if (!task.isSubmitted()) {
            if (!BasicExecutionContext.currentExecutionContext) {
                if (em!=null) {
                    log.warn("Discouraged submission of compound task ($task) from $this without execution context; using execution manager");
                    em.submit(task);
                } else {
                    throw new IllegalStateException("Compound task ($task) launched from $this missing required execution context");
                }
            } else {
                BasicExecutionContext.currentExecutionContext.submit(task)
            }
        }
    }
}
