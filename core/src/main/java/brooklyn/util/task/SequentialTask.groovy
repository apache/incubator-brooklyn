package brooklyn.util.task;

import java.util.Collection;
import java.util.concurrent.Callable

import brooklyn.management.Task


class SequentialTask extends CompoundTask {

	public SequentialTask(Object... tasks) { super(tasks) }
	public SequentialTask(Collection<Object> tasks) { super(tasks) }

	protected Object runJobs() {
		children.each { job -> result.add job.get() }
		return result
	}
}
