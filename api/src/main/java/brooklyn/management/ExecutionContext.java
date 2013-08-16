package brooklyn.management;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;

import brooklyn.entity.Entity;

/**
 * This is a Brooklyn extension to the Java {@link Executor}.
 * 
 * The "context" could, for example, be an {@link Entity} so that tasks executed 
 * can be annotated as executing in that context.
 */
public interface ExecutionContext extends Executor {
    /**
     * Returns the current {@link Task} being executed by this context, or null if not currently executing a task.
     * @deprecated in 0.5, use Tasks.current()
     */
    Task<?> getCurrentTask();

    /**
     * Get the tasks executed through this context (returning an immutable set).
     */
    Set<Task<?>> getTasks();

    /**
     * See {@link ExecutionManager#submit(Map, Task)} for properties that can be passed in.
     */
    Task<?> submit(Map<?, ?> properties, Runnable runnable);

    /**
     * See {@link ExecutionManager#submit(Map, Task)} for properties that can be passed in.
     */
    <T> Task<T> submit(Map<?, ?> properties, Callable<T> callable);

    /** See {@link ExecutionManager#submit(Map, Task)}. */
    <T> Task<T> submit(Task<T> task);
    
    /** See {@link ExecutionManager#submit(Map, Task)}. */
    <T> Task<T> submit(HasTask<T> task);
    
    /**
     * See {@link ExecutionManager#submit(Map, Task)} for properties that can be passed in.
     */
    public <T> Task<T> submit(Map<?, ?> properties, Task<T> task);

}