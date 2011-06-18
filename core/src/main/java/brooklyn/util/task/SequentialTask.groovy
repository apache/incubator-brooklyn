package brooklyn.util.task;

import java.util.concurrent.Callable
import java.util.concurrent.Executor


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
