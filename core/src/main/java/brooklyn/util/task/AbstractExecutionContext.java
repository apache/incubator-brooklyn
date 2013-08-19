package brooklyn.util.task;

import java.util.Map;
import java.util.concurrent.Callable;

import brooklyn.management.ExecutionContext;
import brooklyn.management.ExecutionManager;
import brooklyn.management.Task;
import brooklyn.management.TaskAdaptable;

import com.google.common.collect.Maps;

public abstract class AbstractExecutionContext implements ExecutionContext {

    /**
     * Submits the given runnable/callable/task for execution (in a separate thread);
     * supported keys in the map include: tags (add'l tags to put on the resulting task), 
     * description (string), and others as described in the reference below
     *   
     * @see ExecutionManager#submit(Map, Task) 
     */
    public Task<?> submit(Map<?,?> properties, Runnable runnable) { return submitInternal(properties, runnable); }
    
    /** @see #submit(Map, Runnable) */
    public Task<?> submit(Runnable runnable) { return submitInternal(Maps.newLinkedHashMap(), runnable); }
 
    /** @see #submit(Map, Runnable) */
    public <T> Task<T> submit(Callable<T> callable) { return submitInternal(Maps.newLinkedHashMap(), callable); }
    
    /** @see #submit(Map, Runnable) */
    public <T> Task<T> submit(Map<?,?> properties, Callable<T> callable) { return submitInternal(properties, callable); }
 
    /** @see #submit(Map, Runnable) */
    public <T> Task<T> submit(TaskAdaptable<T> task) { return submitInternal(Maps.newLinkedHashMap(), task.asTask()); }

    /** @see #submit(Map, Runnable) */
    public <T> Task<T> submit(Map<?,?> properties, TaskAdaptable<T> task) { return submitInternal(properties, task.asTask()); }

    /**
     * Provided for compatibility
     * 
     * Submit is preferred if a handle on the resulting Task is desired (although a task can be passed in so this is not always necessary) 
     *
     * @see #submit(Map, Runnable) 
     */
    public void execute(Runnable r) { submit(r); }

    protected abstract <T> Task<T> submitInternal(Map<?,?> properties, Object task);
    
}
