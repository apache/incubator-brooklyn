package brooklyn.util.task;

import java.util.concurrent.Callable

import brooklyn.management.Task

class ParallelTask extends CompoundTask {
	
	public ParallelTask(Task<?>... tasks) { super(tasks) }
	public ParallelTask(Runnable... tasks) { super(tasks) }
	public ParallelTask(Callable<?>... tasks) { super(tasks) }
	public ParallelTask(Closure<?>... tasks) { super(tasks) }

	protected Object runJobs() {
		//TODO
		null
	}	
}
