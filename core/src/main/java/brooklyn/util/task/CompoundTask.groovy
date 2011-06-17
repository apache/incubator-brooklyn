package brooklyn.util.task

import java.util.concurrent.Callable
import java.util.concurrent.Executor

abstract class CompoundTask extends Task<Object> {
	
	protected final List<Task<?>> children;
	
	public CompoundTask(Task<?>... tasks) { /* TODO */ }
	public CompoundTask(Runnable... tasks)    { this( tasks.collect { Runnable it -> new Task(it) } ) }
	public CompoundTask(Callable<?>... tasks) { this( tasks.collect { Callable<?> it -> new Task(it) } ) }
	public CompoundTask(Closure<?>... tasks)  { this( tasks.collect { Closure<?> it -> new Task(it) } ) }

	protected abstract void run(Executor runner);
	
}
