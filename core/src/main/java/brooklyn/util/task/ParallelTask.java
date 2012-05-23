package brooklyn.util.task;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;

import brooklyn.management.Task;

/**
 * Runs {@link Task}s in parallel.
 *
 * No guarantees of order of starting the tasks, but the return value is a
 * {@link List} of the return values of supplied tasks in the same
 * order they were passed as arguments.
 */
public class ParallelTask<T> extends CompoundTask<T> {
    public ParallelTask(Object... tasks) { super(tasks); }
    public ParallelTask(Collection<? extends Object> tasks) { super(tasks); }

    protected List<T> runJobs() throws InterruptedException, ExecutionException {
        for (Task<? extends T> task : children) {
            submitIfNecessary(task);
        }

        List<T> result = new ArrayList<T>();
        for (Task<? extends T> task : children) {
            result.add(task.get());
        }
        return result;
    }
}
