package brooklyn.util.task;

import java.util.Collection

import brooklyn.management.ExecutionManager
import brooklyn.management.Task


class SequentialTask extends CompoundTask {

	public SequentialTask(Object... tasks) { super(tasks) }
	public SequentialTask(Collection<Object> tasks) { super(tasks) }

	protected Object runJobs() {
		List<Object> result = []
		children.each { task ->
			em.submit(task)
			result.add (task.get())
		}
		return result
	}
}
