package brooklyn.management;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

/** 
 * This class manages the execution of a number of jobs with tags.
 * 
 * <p>
 * It is like an executor service (and it ends up delegating to one) but adds additional support
 * where jobs can be:
 * <ul>
 * <li>Tracked with tags/buckets
 * <li>Be {@link Runnable}s, {@link Callable}s, or {@link Closure}s
 * <li>Remembered after completion
 * <li>Treated as {@link Task} instances (see below)
 * <li>Given powerful synchronization capabilities
 * </ul>
 * The advantage of treating them as {@link Task} instances include: 
 * <ul>
 * <li>Richer status information
 * <li>Started-by, contained-by relationships automatically remembered
 * <li>Runtime metadata (start, stop, etc)
 * <li>Grid and multi-machine support) 
 * </ul>
 * <p>
 * For usage instructions see {@link #submit(Map, Task)}, and for examples see the various *ExecutionTest* and *TaskTest* instances.
 * <p>
 * It has been developed for multi-location provisioning and management to track work being
 * done by each {@link Entity}.
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
	 * <li><em>tag</em> - A single object to be used as a tag for looking up the task
	 * <li><em>tags</em> - A {@link Collection} of object tags each of which the task should be associated
XXX	 * <li><em>synchId</em> - A string, or {@link Collection} of strings, representing a category on which an object should own a synch lock 
	 * <li><em>synchObj</em> - A string, or {@link Collection} of strings, representing a category on which an object should own a synch lock 
	 * <li><em>newTaskStartCallback</em> - A {@link Closure} that will be invoked just before the task starts if it starts as a result of this call
	 * <li><em>newTaskEndCallback</em> - A {@link Closure} that will be invoked when the task completes if it starts as a result of this call
	 * </ul>
	 * Callbacks run in the task's thread, and if the callback is a closure it is passed the task for convenience. The closure can be any of the
	 * following types; either a {@link Closure}, {@link Runnable} or {@link Callable}.
	 */
	public Task submit(Map flags, Task task);
}