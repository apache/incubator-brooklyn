package brooklyn.util.task;

import java.util.concurrent.Callable

class SequentialTask extends CompoundTask {
	public SequentialTask(Task... tasks) {}
	public SequentialTask(Runnable... tasks) {}
	public SequentialTask(Callable... tasks) {}
	public SequentialTask(Closure... tasks) {}
}
