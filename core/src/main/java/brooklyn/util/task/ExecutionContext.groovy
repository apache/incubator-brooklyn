package brooklyn.util.task;

import java.util.concurrent.Callable;

/** a means of executing tasks associated with a given bucket */
public class ExecutionContext {

	static final ThreadLocal<ExecutionContext> perThreadExecutionContext = new ThreadLocal<ExecutionContext>()
	
	public static ExecutionManager getCurrentExecutionContext() { return perThreadExecutionContext.get() }
	public static Task getCurrentTask() { return ExecutionManager.getCurrentTask() }

	final ExecutionManager executionManager;
	final Object taskBucket;
	
	public ExecutionContext(ExecutionManager executionManager, Object taskBucket) {
		this.executionManager = executionManager;
		this.taskBucket = taskBucket;
	}

	public Set<Task> getTasksInBucket() { executionManager.getTasksInBucket(taskBucket) }
	
	public Task submit(Runnable r) { executionManager.submit taskBucket, r, newTaskCallback: this.&registerPerThreadExecutionContext }
	public Task submit(Callable r) { executionManager.submit taskBucket, r, newTaskCallback: this.&registerPerThreadExecutionContext }
	public Task submit(Task task) { executionManager.submit taskBucket, task, newTaskCallback: this.&registerPerThreadExecutionContext }
	
	private void registerPerThreadExecutionContext() { perThreadExecutionContext.set this }  
}
