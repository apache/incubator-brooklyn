package brooklyn.util.task

import java.util.Map
import java.util.Queue
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

import brooklyn.management.ExecutionManager
import brooklyn.management.Task

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
 *
 * @deprecated in 0.4.0, use SingleThreadedScheduler
 */
@Deprecated // use SingleThreadedScheduler; FIXME delete this class when we're definitely happy with SingleThreadedScheduler
public class SingleThreadedExecution implements TaskPreprocessor {
    Queue<String> order = new ConcurrentLinkedQueue<String>()
    AtomicBoolean executing = new AtomicBoolean(false)
    
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
