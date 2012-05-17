package brooklyn.util.task;

import brooklyn.management.Task

/**
 * Runs {@link Task}s in parallel.
 *
 * No guarantees of order of starting the tasks, but the return value is a
 * {@link List} of the return values of supplied tasks in the same
 * order they were passed as arguments.
 */
public class ParallelTask extends CompoundTask {
    public ParallelTask(Object... tasks) { super(tasks) }
    public ParallelTask(Collection<Object> tasks) { super(tasks) }

    protected Object runJobs() {
        NavigableMap<Integer,Object> results = [:] as TreeMap
        children.each { task -> submitIfNecessary(task) }
//        while (!children.every { task -> task.isDone() })
        return children.collect { task -> task.get() }
    }
}
