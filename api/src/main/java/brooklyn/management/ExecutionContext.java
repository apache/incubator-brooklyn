package brooklyn.management;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;

/**
 * This is a Brooklyn extension to the Java {@link Executor}.
 */
public interface ExecutionContext extends Executor {
    /**
     * Returns the current {@link Task} being executed by this context.
     */
    Task<?> getCurrentTask();

    Set<Task<?>> getTasks();

    Task<?> submit(Map<?, ?> properties, Runnable runnable);

    <T> Task<T> submit(Map<?, ?> properties, Callable<T> callable);

    <T> Task<T> submit(Task<T> task);
}