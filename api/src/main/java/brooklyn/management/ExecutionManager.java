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
	public Set<Task> getTaskTags();
	public Set<Task> getAllTasks();

	public Task submit(Runnable r);
	public Task submit(Callable r);
	public Task submit(Task task);
	
	public Task submit(Map flags, Runnable r);
	public Task submit(Map flags, Callable r);
	/** submits the gives task associated with the given bucket; 
	 * following optional flags supported.
	 * <p>
	 * tag: a single object to be used as a tag for looking up the task
	 * tags:  a collection of object tags each of which the task should be associated 
	 * newTaskStartCallback: a runnable (or callable) who will be invoked just before the task starts if it starts as a result of this call
	 * newTaskEndCallback: a runnable (or callable) who will be invoked when the task completes if it starts as a result of this call
	 * <p>
	 * callbacks run in the task's thread, and if the callback is a closure it is passed the Task for convenience
	 */
	public Task submit(Map flags, Task task);
}