package brooklyn.util.task;

import java.util.concurrent.Callable

import brooklyn.management.ExecutionContext
import brooklyn.management.ExecutionManager
import brooklyn.management.Task

/**
 * A means of executing tasks against an ExecutionManager with a given bucket/set of tags pre-defined
 * (so that it can look like an {@link Executor} and also supply {@link ExecutorService#submit(Callable)}
 */
public class BasicExecutionContext implements ExecutionContext {
    static final ThreadLocal<ExecutionContext> perThreadExecutionContext = new ThreadLocal<ExecutionContext>()
    
    public static BasicExecutionContext getCurrentExecutionContext() { return perThreadExecutionContext.get() }
 
    public Task getCurrentTask() { return BasicExecutionManager.getCurrentTask() }

    final ExecutionManager executionManager;
    final Set<Object> tags = [];
    
    /**
     * Supported flags are {@code tag} and {@code tags}
     * 
     * @see ExecutionManager#submit(Map, Task)
     */
    public BasicExecutionContext(Map flags=[:], ExecutionManager executionManager) {
        this.executionManager = executionManager;

        if (flags.tag) tags.add flags.remove("tag")
        if (flags.tags) tags.addAll flags.remove("tags")
        if (flags) throw new IllegalArgumentException("Unsupported flags passed to task: "+flags)
    }

    /** returns tasks started by this context (or tasks which have all the tags on this object) */
    public Set<Task> getTasks() { executionManager.getTasksWithAllTags(tags) }

    //these conform with ExecutorService but we do not want to expose shutdown etc here
    /**
     * Submits the given runnable/callable/task for execution (in a separate thread);
     * supported keys in the map include: tags (add'l tags to put on the resulting task), 
     * description (string), and others as described in the reference below
     *   
     * @see ExecutionManager#submit(Map, Task) 
     */
    public Task submit(Map m=[:], Runnable r) { submitInternal(m, r) }
 
    /** @see #submit(Map, Runnable) */
    public Task submit(Map m=[:], Callable r) { submitInternal(m, r) }
 
    /** @see #submit(Map, Runnable) */
    public Task submit(Task task) { submitInternal([:], task) }
 
    private Task submitInternal(Map m, Object r) {
        if (m.tags==null) m.tags = []; 
        m.tags.addAll(tags)
        def oldNTSC = m.newTaskStartCallback;
        m.newTaskStartCallback = { this.registerPerThreadExecutionContext(); if (oldNTSC!=null) oldNTSC.call(it); }
        def oldNTEC = m.newTaskEndCallback;
        m.newTaskEndCallback = { try { if (oldNTEC!=null) oldNTEC.call(it); } finally { this.clearPerThreadExecutionContext() } }
        executionManager.submit m, r
    }

    /**
     * Provided for compatibility
     * 
     * Submit is preferred if a handle on the resulting Task is desired (although a task can be passed in so this is not always necessary) 
     *
     * @see #submit(Map, Runnable) 
     */
    public void execute(Runnable r) { submit r }
    
    private void registerPerThreadExecutionContext() { perThreadExecutionContext.set this }

    private void clearPerThreadExecutionContext() { perThreadExecutionContext.remove() }
}
