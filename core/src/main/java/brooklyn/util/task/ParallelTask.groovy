package brooklyn.util.task;

import java.util.concurrent.Callable

import brooklyn.management.Task


class ParallelTask extends CompoundTask {
	
	public ParallelTask(Object... tasks) { super(tasks) }
	public ParallelTask(Collection<Object> tasks) { super(tasks) }

	protected Object runJobs() {
		//TODO
		null
	}	
}
