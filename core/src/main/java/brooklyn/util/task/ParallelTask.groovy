package brooklyn.util.task;

import java.util.concurrent.Callable
import java.util.concurrent.Executor;

class ParallelTask extends CompoundTask {
	public ParallelTask(Task... tasks) {}
	public ParallelTask(Runnable... tasks) {}
	public ParallelTask(Callable... tasks) {}
	public ParallelTask(Closure... tasks) {}
	
	public void run(Executor runner) {
		future = runner.execute task
	}
}
