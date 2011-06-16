package brooklyn.util.task;

import java.util.concurrent.Callable;
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import brooklyn.entity.Entity
import brooklyn.util.internal.LanguageUtils;


public class ExecutionManager {
	
	private static final perThreadCurrentTask = new ThreadLocal<Task>()
	public static Task getCurrentTask() { return perThreadCurrentTask.get() }
	
	private ExecutorService runner = Executors.newCachedThreadPool() 
	
	private Set<Task> knownTasks = new LinkedHashSet()
	private Map<Object,Set<Task>> tasksByBucket = new LinkedHashMap()
	//access to the above is synchronized in code in this class, to allow us to preserve order while guaranteeing thread-safe
	//(but more testing is needed before we are sure it is thread-safe!)
	//synch blocks are as finely grained as possible for efficiency
	
	public Set<Task> getTasksByBucket(Object bucket) {
		Set<Task> tasksInBucket;
		synchronized (tasksByBucket) {
			tasksInBucket = tasksByBucket.get(bucket)
		}
		if (tasksInBucket==null) return Collections.emptySet()
		synchronized (tasksInBucket) {
			return new LinkedHashSet(tasksInBucket)
		} 
	}
	public Set<Task> getTaskBuckets() { synchronized (tasksByBucket) { return new LinkedHashSet(tasksByBucket.keySet()) }}
	public Set<Task> getAllTasks() { synchronized (knownTasks) { return new LinkedHashSet(knownTasks) }}
	
	public Task submit(Map flags=[:], Object bucket, Runnable r) { submit bucket, new Task(r) }
	public Task submit(Map flags=[:], Object bucket, Callable r) { submit bucket, new Task(r) }
	/** submits the gives task associated with the given bucket; 
	 * following optional flags supported.
	 * <p>
	 * newTaskStartCallback: a runnable (or callable) who will be invoked just before the task starts if it starts as a result of this call
	 * newTaskEndCallback: a runnable (or callable) who will be invoked when the task completes if it starts as a result of this call
	 * <p>
	 * callbacks run in the task's thread, and if the callback is a closure it is passed the Task for convenience
	 */
	public Task submit(Map flags=[:], Object bucket, Task task) {
		if (task.result!=null) return task
		synchronized (task) {
			if (task.result!=null) return task
			submitNewTask flags, bucket, task
		}
	}

	protected Task submitNewTask(Map flags, Object bucket, Task task) {
		beforeSubmit(flags, bucket, task)
		Closure job = { try { beforeStart(flags, task); task.job.call() } finally { afterEnd(flags, task) } }
		task.initResult(runner.submit(job as Callable))   //need as Callable to make sure we get the return type; otherwise closure may be treated as Runnable
		return task
	}

	protected void beforeSubmit(Map flags, Object bucket, Task task) {
		task.submittedByTask = getCurrentTask()
		task.submitTimeUtc = System.currentTimeMillis()
		synchronized (knownTasks) {
			knownTasks << task
		}
		Set set
		synchronized (tasksByBucket) {
			set = tasksByBucket.get bucket;
			if (set==null) {
				set = new LinkedHashSet()
				tasksByBucket.put bucket, set
			}
		}
		synchronized (set) {
			set << task
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
