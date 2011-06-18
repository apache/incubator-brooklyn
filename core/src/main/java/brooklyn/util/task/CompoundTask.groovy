package brooklyn.util.task

import java.util.concurrent.Callable

public abstract class CompoundTask extends Task<Object> {
	
	protected final Collection<Task<?>> children;
	
	public CompoundTask(Collection<Task<?>> children) { super( { runJobs() } ); this.children = children }
	public CompoundTask(Task<?>... tasks)    { this( tasks.collect { Runnable it -> new Task(it) } ) }
	public CompoundTask(Runnable... tasks)    { this( tasks.collect { Runnable it -> new Task(it) } ) }
	public CompoundTask(Callable<?>... tasks) { this( tasks.collect { Callable<?> it -> new Task(it) } ) }
	public CompoundTask(Closure<?>... tasks)  { this( tasks.collect { Closure<?> it -> new Task(it) } ) }

	protected abstract Object runJobs()
	
}
