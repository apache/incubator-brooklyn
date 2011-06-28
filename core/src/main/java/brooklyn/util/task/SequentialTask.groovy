package brooklyn.util.task;

import java.util.Collection

import brooklyn.management.ExecutionManager
import brooklyn.management.Task


/** runs tasks in order, waiting for one to finish before starting the next; return value here is TBD;
 * (currently is all the return values of individual tasks, but we
 * might want some pipeline support and eventually only to return final value...) */
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
