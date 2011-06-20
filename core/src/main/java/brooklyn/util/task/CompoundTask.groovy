package brooklyn.util.task

import java.util.concurrent.Callable

import brooklyn.management.Task


public abstract class CompoundTask extends BasicTask<Object> {
	
	protected final Collection<Task> children;
	
	public CompoundTask(Collection<Task> children) { super( { runJobs() } ); this.children = children }
	
	public CompoundTask(Task... tasks)    { this( tasks.collect { it } ) }
	public CompoundTask(Runnable... tasks)    { this( tasks.collect { Runnable it -> new BasicTask(it) } ) }
	public CompoundTask(Callable<?>... tasks) { this( tasks.collect { Callable<?> it -> new BasicTask(it) } ) }
//	public CompoundTask(Closure<?>... tasks)  { this( tasks.collect { Closure<?> it -> new Task(it) } ) }

	protected abstract Object runJobs()
	
}
