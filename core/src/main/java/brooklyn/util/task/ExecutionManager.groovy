package brooklyn.util.task;

import java.util.concurrent.Callable;
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import brooklyn.entity.Entity


public class ExecutionManager {
	
	private static final perThreadCurrentTask = new ThreadLocal<Task>()
	public static Task getCurrentTask() { return perThreadCurrentTask.get() }
	
	ExecutorService runner = Executors.newCachedThreadPool() 
	
	Set<Task> knownTasks = new LinkedHashSet()
	Map<Object,Set<Task>> tasksByBucket = new LinkedHashMap()
	//FIXME synch on the per-bucket task list?
	Set<Task> getTasksByBucket(Object bucket) { return new LinkedHashSet(tasksByBucket.get(bucket)) ?: Collections.emptySet() }
	public Set<Task> getTaskBuckets() { synchronized (tasksByBucket) { return new LinkedHashSet(tasksByBucket.keySet()) }}
	
	public Task submit(Map flags=[:], Object bucket, Runnable r) { submit bucket, new Task(r) }
	public Task submit(Map flags=[:], Object bucket, Callable r) { submit bucket, new Task(r) }
	/** submits the gives task associated with the given bucket; 
	 * following optional flags supported.
	 * <p>
	 * newTaskCallback: a runnable (or callable) who will be invoked if the task is starting as a result of this call
	 */
	public Task submit(Map flags=[:], Object bucket, Task task) {
		if (task.result!=null) return task
		synchronized (task) {
			if (task.result!=null) return task
			submitNewTask flags, bucket, task
		}
	}

	protected Task submitNewTask(Map flags, Object bucket, Task task) {
		if (flags.newTaskCallback) { def cb = flags.newTaskCallback; if (cb in Callable) cb.call() else cb.run() } 
		
		perThreadCurrentTask.set task
		synchronized (knownTasks) {
			knownTasks << task
		}
		synchronized (tasksByBucket) {
			Set set = tasksByBucket.get bucket;
			if (set==null) {
				set = new LinkedHashSet()
				tasksByBucket.put bucket, set
			}
			set << task
		}
		task.result = runner.submit(task.job as Callable)
		return task
	}	
	
//	CompoundTask execute(Entity entity, CompoundTask tasks) {
//		tasks.subTasks.each { it.run(runner) }
//		tasks
//	}
	
}
