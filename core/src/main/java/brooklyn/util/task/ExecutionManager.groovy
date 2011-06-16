package brooklyn.util.task;

import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

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
public class ExecutionManager {
	
	private static final perThreadCurrentTask = new ThreadLocal<Task>()
	public static Task getCurrentTask() { return perThreadCurrentTask.get() }
	
	private ExecutorService runner = Executors.newCachedThreadPool() 
	
	private Set<Task> knownTasks = new LinkedHashSet()
	private Map<Object,Set<Task>> tasksByTag = new LinkedHashMap()
	//access to the above is synchronized in code in this class, to allow us to preserve order while guaranteeing thread-safe
	//(but more testing is needed before we are sure it is thread-safe!)
	//synch blocks are as finely grained as possible for efficiency
	
	public Set<Task> getTasksWithTag(Object tag) {
		Set<Task> tasksWithTag;
		synchronized (tasksByTag) {
			tasksWithTag = tasksByTag.get(tag)
		}
		if (tasksWithTag==null) return Collections.emptySet()
		synchronized (tasksWithTag) {
			return new LinkedHashSet(tasksWithTag)
		} 
	}
	public Set<Task> getTasksWithAnyTag(Iterable tags) {
		Set result = []
		tags.each { tag -> result.addAll( getTasksWithTag(tag) ) }
		result
	}
	public Set<Task> getTasksWithAllTags(Iterable tags) {
		//NB: for this method retrieval for multiple tags could be made (much) more efficient (if/when it is used with multiple tags!)
		//by first looking for the least-used tag, getting those tasks, and then for each of those tasks 
		//checking whether it contains the other tags (looking for second-least used, then third-least used, etc)
		Set result = null
		tags.each {
			tag ->
			if (result==null) result = getTasksWithTag(tag)
			else {
				result.retainAll getTasksWithTag(tag)
				if (!result) return result  //abort if we are already empty
			} 
		}
		result
	}
	public Set<Task> getTaskTags() { synchronized (tasksByTag) { return new LinkedHashSet(tasksByTag.keySet()) }}
	public Set<Task> getAllTasks() { synchronized (knownTasks) { return new LinkedHashSet(knownTasks) }}
	
	public Task submit(Map flags=[:], Runnable r) { submit flags, new Task(r) }
	public Task submit(Map flags=[:], Callable r) { submit flags, new Task(r) }
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
	public Task submit(Map flags=[:], Task task) {
		if (task.result!=null) return task
		synchronized (task) {
			if (task.result!=null) return task
			submitNewTask flags, task
		}
	}

	protected Task submitNewTask(Map flags, Task task) {
		beforeSubmit(flags, task)
		Closure job = { try { beforeStart(flags, task); task.job.call() } finally { afterEnd(flags, task) } }
		task.initResult(runner.submit(job as Callable))   //need as Callable to make sure we get the return type; otherwise closure may be treated as Runnable
		return task
	}

	protected void beforeSubmit(Map flags, Task task) {
		task.submittedByTask = getCurrentTask()
		task.submitTimeUtc = System.currentTimeMillis()
		synchronized (knownTasks) {
			knownTasks << task
		}
		if (flags.tag) task.@tags.add flags.remove("tag")
		if (flags.tags) task.@tags.addAll flags.remove("tags")

		List tagBuckets = []
		synchronized (tasksByTag) {
			task.@tags.each { tag ->
				Set tagBucket = tasksByTag.get tag
				if (tagBucket==null) {
					tagBucket = new LinkedHashSet()
					tasksByTag.put tag, tagBucket
				}
				tagBuckets.add tagBucket
			}
		}
		tagBuckets.each { bucket ->
			synchronized (bucket) {
				bucket << task
			}
		}
	}	
	protected void beforeStart(Map flags, Task task) {
		task.startTimeUtc = System.currentTimeMillis()
		perThreadCurrentTask.set task
		ExecutionUtils.invoke flags.newTaskStartCallback, task
	}

	protected void afterEnd(Map flags, Task task) {
		ExecutionUtils.invoke flags.newTaskEndCallback, task
		perThreadCurrentTask.remove()
		task.endTimeUtc = System.currentTimeMillis()
		synchronized (task) { task.notifyAll() }
	}

}
