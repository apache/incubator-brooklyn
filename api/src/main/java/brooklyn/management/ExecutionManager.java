package brooklyn.management;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;

import brooklyn.entity.Entity;

/** 
 * This class manages the execution of a number of jobs with tags.
 * 
 * It is like an executor service (and it ends up delegating to one) but adds additional support
 * where jobs can be:
 * <ul>
 * <li>Tracked with tags/buckets
 * <li>Be {@link Runnable}s, {@link Callable}s, or {@link groovy.lang.Closure}s
 * <li>Remembered after completion
 * <li>Treated as {@link Task} instances (see below)
 * <li>Given powerful synchronization capabilities
 * </ul>
 * <p>
 * The advantage of treating them as {@link Task} instances include: 
 * <ul>
 * <li>Richer status information
 * <li>Started-by, contained-by relationships automatically remembered
 * <li>Runtime metadata (start, stop, etc)
 * <li>Grid and multi-machine support) 
 * </ul>
 * <p>
 * For usage instructions see {@link #submit(Map, Task)}, and for examples see the various
 * {@code ExecutionTest} and {@code TaskTest} instances.
 * <p>
 * It has been developed for multi-location provisioning and management to track work being
 * done by each {@link Entity}.
 * <p>
 * Note the use of the environment variable {@code THREAD_POOL_SIZE} which is used to size
 * the {@link ExecutorService} thread pool. The default is calculated as twice the number
 * of CPUs in the system plus two, giving 10 for a four core system, 18 for an eight CPU
 * server and so on.
 */
public interface ExecutionManager {
    public boolean isShutdown();
    
    /** returns the task with the given ID, or null if none */ 
    public Task<?> getTask(String id);
    
    /** returns all tasks with the given tag (immutable) */
    public Set<Task<?>> getTasksWithTag(Object tag);

    /** returns all tasks that have any of the given tags (immutable) */
    public Set<Task<?>> getTasksWithAnyTag(Iterable<?> tags);

    /** returns all tasks that have all of the given tags (immutable) */
    public Set<Task<?>> getTasksWithAllTags(Iterable<?> tags);

    /** returns all tags known to this manager (immutable) */
    public Set<Object> getTaskTags();

//    /** returns all tasks known to this manager (immutable) */
//    public Set<Task<?>> getAllTasks();

    /** see {@link #submit(Map, Task)} */
    public Task<?> submit(Runnable r);

    /** see {@link #submit(Map, Task)} */
    public <T> Task<T> submit(Callable<T> c);

    /** see {@link #submit(Map, Task)} */
    public <T> Task<T> submit(TaskAdaptable<T> task);
    
    /** see {@link #submit(Map, Task)} */
    public Task<?> submit(Map<?, ?> flags, Runnable r);

    /** see {@link #submit(Map, Task)} */
    public <T> Task<T> submit(Map<?, ?> flags, Callable<T> c);

    /**
     * Submits the given {@link Task} for execution in the context associated with this manager.
     *
     * The following optional flags supported (in the optional map first arg):
     * <ul>
     * <li><em>tag</em> - A single object to be used as a tag for looking up the task
     * <li><em>tags</em> - A {@link Collection} of object tags each of which the task should be associated,
     *                      used for associating with contexts, mutex execution, and other purposes
     * <li><em>synchId</em> - A string, or {@link Collection} of strings, representing a category on which an object should own a synch lock 
     * <li><em>synchObj</em> - A string, or {@link Collection} of strings, representing a category on which an object should own a synch lock 
     * <li><em>newTaskStartCallback</em> - A {@link groovy.lang.Closure} that will be invoked just before the task starts if it starts as a result of this call
     * <li><em>newTaskEndCallback</em> - A {@link groovy.lang.Closure} that will be invoked when the task completes if it starts as a result of this call
     * </ul>
     * Callbacks run in the task's thread, and if the callback is a {@link groovy.lang.Closure} it is passed the task for convenience. The closure can be any of the
     * following types; either a {@link groovy.lang.Closure}, {@link Runnable} or {@link Callable}.
     * <p>
     * If a Map is supplied it must be modifiable (currently; may allow immutable maps in future). 
     */
    public <T> Task<T> submit(Map<?, ?> flags, TaskAdaptable<T> task);

}