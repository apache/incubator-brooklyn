package brooklyn.util.task;

import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

/** a means of executing tasks against an ExecutionManager with a given bucket/set of tags pre-defined
 * (so that it can look like an {@link Executor} and also supply {@link ExecutorService#submit(Callable)} */
public class ExecutionContext implements Executor {

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

	//these conform with ExecutorService but we do not want to expose shutdown etc here
	public Task submit(Runnable r) { submitInternal(r) }
	public Task submit(Callable r) { submitInternal(r) }
	public Task submit(Task task) { submitInternal(task) }
	private Task submitInternal(Object r) {
		executionManager.submit taskBucket, r, 
			newTaskStartCallback: this.&registerPerThreadExecutionContext,
			newTaskEndCallback: this.&clearPerThreadExecutionContext
	}

	/** provided for compatibility; submit is preferred if a handle on the resulting Task is desired (although a task can be passed in so this is not always necessary) */
	public void execute(Runnable r) { submit r }
	
	private void registerPerThreadExecutionContext() { perThreadExecutionContext.set this }  
	private void clearPerThreadExecutionContext() { perThreadExecutionContext.remove() }
}
