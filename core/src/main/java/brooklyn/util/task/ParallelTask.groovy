package brooklyn.util.task;

import brooklyn.management.Task

class ParallelTask extends CompoundTask {
	
	public ParallelTask(Object... tasks) { super(tasks) }
	public ParallelTask(Collection<Object> tasks) { super(tasks) }

	protected Object runJobs() {
		List<Object> result = []
		children.each { task ->	em.submit(task) }
		children.each { task ->	result.add(task.get()) }
		return result
	}	
}
