package brooklyn.util.task;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import brooklyn.management.Task;

/**
 * The scheduler is an internal mechanism to decorate {@link Task}s.
 *
 * It can control how the tasks are scheduled for execution (e.g. single-threaded execution,
 * prioritised, etc).
 */
public interface TaskScheduler {
    
    public void injectExecutor(ExecutorService executor);

    /**
     * Called by {@link BasicExecutionManager} to schedule tasks.
     */
    public <T> Future<T> submit(Callable<T> c);
}
