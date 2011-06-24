package brooklyn.management;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

/** This class manages the execution of a number of jobs--with tags.
 * It is like an executor service (and it ends up delegating to one) but adds additional support
 * where jobs can be:
 * - tracked with tags/buckets
 * - be Runnables, Callables, or Closures
 * - remembered after completion
 * - treated as {@link Task} instances (see below)
 * <p>  
 * The advantage of treating them as {@link Task} instances include: 
 * - richer status information
 * - started-by, contained-by relationships automatically remembered
 * - runtime metadata (start, stop, etc)
 * - grid / multi-machine support) 
 * <p>
 * (It has been developed for the Brooklyn multi-location provisioning and management project,
 * to track work being done by each Entity.)
 *  
 * @author alex
 */
@SuppressWarnings("rawtypes")
public interface ExecutionManager {
	public Set<Task> getTasksWithTag(Object tag);
	public Set<Task> getTasksWithAnyTag(Iterable tags);
	public Set<Task> getTasksWithAllTags(Iterable tags);
	/** returns all tags known to this manager (immutable) */
	public Set<Object> getTaskTags();
	/** returns all tasks known to this manager (immutable) */
	public Set<Task> getAllTasks();

	/** see {@link #submit(Map, Task)} */
	public Task submit(Runnable r);
	/** see {@link #submit(Map, Task)} */
	public Task submit(Callable r);
	/** see {@link #submit(Map, Task)} */
	public Task submit(Task task);
	
	/** see {@link #submit(Map, Task)} */
	public Task submit(Map flags, Runnable r);
	/** see {@link #submit(Map, Task)} */
	public Task submit(Map flags, Callable r);

	/**
	 * Submits the given {@link Task} associated with the given bucket.
	 *
	 * The following optional flags supported.
	 * <ul>
	 * <li><em>tag</em> - a single object to be used as a tag for looking up the task
	 * <li><em>tags</em> - a collection of object tags each of which the task should be associated
	 * <li><em>newTaskStartCallback</em> - a closure that will be invoked just before the task starts if it starts as a result of this call
	 * <li><em>newTaskEndCallback</em> - a closure that will be invoked when the task completes if it starts as a result of this call
	 * </ul>
	 * Callbacks run in the task's thread, and if the callback is a closure it is passed the Task for convenience. The closure can be any of the
	 * following types; either a {@link Closure}, {@link Runnable} or {@link Callable}.
	 */
	public Task submit(Map flags, Task task);
}