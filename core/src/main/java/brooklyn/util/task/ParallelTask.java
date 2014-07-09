package brooklyn.util.task;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import brooklyn.management.Task;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.text.Strings;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

/**
 * Runs {@link Task}s in parallel.
 *
 * No guarantees of order of starting the tasks, but the return value is a
 * {@link List} of the return values of supplied tasks in the same
 * order they were passed as arguments.
 */
public class ParallelTask<T> extends CompoundTask<T> {
    public ParallelTask(Object... tasks) { super(tasks); }
    
    public ParallelTask(Map<String,?> flags, Collection<? extends Object> tasks) { super(flags, tasks); }
    public ParallelTask(Collection<? extends Object> tasks) { super(tasks); }
    
    public ParallelTask(Map<String,?> flags, Iterable<? extends Object> tasks) { super(flags, ImmutableList.copyOf(tasks)); }
    public ParallelTask(Iterable<? extends Object> tasks) { super(ImmutableList.copyOf(tasks)); }

    @Override
    protected List<T> runJobs() throws InterruptedException, ExecutionException {
        setBlockingDetails("Executing "+
                (children.size()==1 ? "1 child task" :
                children.size()+" children tasks in parallel") );
        for (Task<? extends T> task : children) {
            submitIfNecessary(task);
        }

        List<T> result = Lists.newArrayList();
        List<Exception> exceptions = Lists.newArrayList();
        for (Task<? extends T> task : children) {
            T x;
            try {
                x = task.get();
            } catch (Exception e) {
                Exceptions.propagateIfFatal(e);
                exceptions.add(e);
                x = null;
            }
            result.add(x);
        }
        
        if (exceptions.isEmpty()) {
            return result;
        } else {
            if (result.size()==1 && exceptions.size()==1)
                throw Exceptions.propagate( exceptions.get(0) );
            throw Exceptions.propagate(exceptions.size()+" of "+result.size()+" parallel child task"+Strings.s(result.size())+" failed", exceptions);
        }
    }
}
