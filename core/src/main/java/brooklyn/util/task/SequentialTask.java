package brooklyn.util.task;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;

import brooklyn.management.Task;


/** runs tasks in order, waiting for one to finish before starting the next; return value here is TBD;
 * (currently is all the return values of individual tasks, but we
 * might want some pipeline support and eventually only to return final value...) */
public class SequentialTask<T> extends CompoundTask<T> {

    public SequentialTask(Object... tasks) { super(tasks); }
    public SequentialTask(Collection<Object> tasks) { super(tasks); }

    protected List<T> runJobs() throws InterruptedException, ExecutionException {
        setBlockingDetails("Executing "+
                (children.size()==1 ? "1 child task" :
                children.size()+" children tasks sequentially") );

        List<T> result = new ArrayList<T>();
        for (Task<? extends T> task : children) {
            submitIfNecessary(task);
            // throw exception (and cancel subsequent tasks) on error
            result.add(task.get());
        }
        return result;
    }
}
