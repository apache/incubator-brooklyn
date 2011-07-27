package brooklyn.util.task;

import java.util.Map
import java.util.Queue
import java.util.concurrent.ConcurrentLinkedQueue

import brooklyn.management.ExecutionManager
import brooklyn.management.Task

/**
 * The preprocessor is an internal mechanism to decorate {@link Task}s.
 *
 * This can be used to enhance tasks that they acquire a {@code synchronized} block (to cause
 * them to effectively run singly-threaded), or clear intermediate queued jobs, etc.
 */
public interface TaskPreprocessor {
    /**
     * Called by {@link BasicExecutionManager} when preprocessor is associated with an
     * execution manager.
     */
    public void injectManager(ExecutionManager m);

    /**
     * Called by {@link BasicExecutionManager} when preprocessor is associated with a tag.
     */
    public void injectTag(Object tag);

    /**
     * Called by {@link BasicExecutionManager} when task is submitted in the category, in
     * order of tags.
     */
    public void onSubmit(Map flags, Task task);

    /**
     * Called by {@link BasicExecutionManager} when task is started in the category, in
     * order of tags.
     */
    public void onStart(Map flags, Task task);

    /**
     * Called by {@link BasicExecutionManager} when task is ended in the category, in
     * <em>reverse</em> order of tags.
     */
    public void onEnd(Map flags, Task task);

}

/**
 * Instances of this class ensures that {@link Task}s it is shown execute with in-order
 * single-threaded semantics.
 *
 * Tasks can be presented through {@link #onSubmit(Map)}, {@link #onStart(Map)}, and
 * {@link #onEnd(Map)} (but not necessarily all in the same thread).  The order is that in which
 * it is submitted.
 * <p>
 * This implementation does so by blocking on a {@link ConcurrentLinkedQueue}, <em>after</em>
 * the task is started in a thread (and {@link Task#isStarted()} returns true), but (of course)
 * <em>before</em> the {@link Task#job} actually gets invoked.
 */
public class SingleThreadedExecution implements TaskPreprocessor {
    Queue<String> order = new ConcurrentLinkedQueue<String>()

    ExecutionManager manager
    Object tag

    public void injectManager(ExecutionManager manager) { this.manager = manager }

    public void injectTag(Object tag) { this.tag = tag }

    public void onSubmit(Map flags=[:], Task task) { order.add(task.id) }

    public void onStart(Map flags=[:], Task task) {
        task.blockingDetails = "single threaded category, "+order.size()+" elements ahead of us when submitted"
        synchronized (task.id) {
            String next = order.peek();
            while (next != task.id) {
                task.id.wait()
                next = order.peek()
            }
        }
        task.blockingDetails = null
    }

    public void onEnd(Map flags=[:], Task task) {
        String last = order.remove()
        assert last == task.id
        String next = order.peek()
        if (next != null) {
            synchronized (next) {
                next.notifyAll()
            }
        }
    }
}
