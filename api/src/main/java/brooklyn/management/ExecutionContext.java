package brooklyn.management;

import java.util.concurrent.Executor;

/**
 * This is a Brooklyn extension to the Java {@link Executor}.
 */
public interface ExecutionContext extends Executor { 
    /**
     * Returns the current {@link Task} being executed by this context.
     */
    Task<?> getCurrentTask();
}