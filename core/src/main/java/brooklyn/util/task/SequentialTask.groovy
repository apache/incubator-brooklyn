package brooklyn.util.task;

import java.util.concurrent.Callable

import brooklyn.management.Task


class SequentialTask extends CompoundTask {
	
	public SequentialTask(Task<?>... tasks) { super(tasks) }
	public SequentialTask(Runnable... tasks) { super(tasks) }
	public SequentialTask(Callable<?>... tasks) { super(tasks) }
	public SequentialTask(Closure<?>... tasks) { super(tasks) }
	
	protected Object runJobs() {
		//TODO
		null
	}	
}
