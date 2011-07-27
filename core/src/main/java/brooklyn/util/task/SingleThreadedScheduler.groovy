package brooklyn.util.task

import java.util.Queue
import java.util.concurrent.Callable
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean

import org.slf4j.Logger
import org.slf4j.LoggerFactory

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
public class SingleThreadedScheduler implements TaskScheduler {
    private static final Logger LOG = LoggerFactory.getLogger(SingleThreadedScheduler.class)
    
    private final Queue<QueuedSubmission> order = new ConcurrentLinkedQueue<QueuedSubmission>()
    private final AtomicBoolean running = new AtomicBoolean(false)
    
    private ExecutorService executor

    public void injectExecutor(ExecutorService executor) { this.executor = executor }

    public synchronized <T> Future<T> submit(Callable<T> c) {
        if (running.compareAndSet(false, true)) {
            return executeNow(c)
        } else {
            WrappingFuture f = new WrappingFuture<T>()
            order.add(new QueuedSubmission(c, f))
            return f
        }
        if(order.size() > 10) LOG.info "$this is backing up, $order.size() tasks queued"
    }

    private synchronized void onEnd() {
        boolean done = false
        while (!done) {
            if (order.isEmpty()) {
                running.set(false)
                done = true
            } else {
                QueuedSubmission qs = order.remove()
                if (!qs.f.isCancelled()) {
                    Future future = executeNow(qs.c)
                    qs.f.setDelegate(future)
                    done = true
                }
            }
        }
    }

    private synchronized <T> Future<T> executeNow(Callable<T> c) {
        return executor.submit( {
            try {
                return c.call()
            } finally {
                onEnd()
            }} as Callable)
    }
    
    
    private static class QueuedSubmission {
        final Callable c;
        final WrappingFuture f;
        
        QueuedSubmission(Callable c, WrappingFuture f) {
            this.c = c;
            this.f = f;
        }
    }
    
    /**
     * A future, where the task may not yet have been submitted to the real executor.
     * It delegates to the real future if present, and otherwise waits for that to appear
     */
    private static class WrappingFuture<T> implements Future<T> {
        private volatile Future<T> delegate
        private boolean cancelled
        
        void setDelegate(Future<T> delegate) {
            synchronized (this) {
                this.delegate = delegate;
                notifyAll()
            }
        }
        
        @Override public boolean cancel(boolean mayInterruptIfRunning) {
            if (delegate) {
                return delegate.cancel(mayInterruptIfRunning)
            } else {
                cancelled = true
                synchronized (this) {
                    notifyAll()
                }
                return
            }
        }
        @Override public boolean isCancelled() {
            if (delegate) {
                return delegate.isCancelled()
            } else {
                return cancelled
            }
        }
        @Override public boolean isDone() {
            return (delegate) ? delegate.isDone() : cancelled
        }
        @Override public T get() {
            if (cancelled) {
                throw new CancellationException()
            } else if (delegate) {
                return delegate.get()
            } else {
                synchronized (this) {
                    while (delegate == null && !cancelled) {
                        wait()
                    }
                }
                return get()
            }
        }
        @Override public T get(long timeout, TimeUnit unit) {
            long endtime = System.currentTimeMillis()+unit.toMillis(timeout)
            
            if (cancelled) {
                throw new CancellationException()
            } else if (delegate) {
                return delegate.get(timeout, unit)
            } else if (System.currentTimeMillis() >= endtime) {
                throw new TimeoutException()
            } else {
                synchronized (this) {
                    while (delegate == null && !cancelled && System.currentTimeMillis() < endtime) {
                        long remaining = endtime - System.currentTimeMillis()
                        if (remaining > 0) {
                            wait(remaining)
                        }
                    }
                }
                long remaining = endtime - System.currentTimeMillis()
                return get(remaining, TimeUnit.MILLISECONDS)
            }
        }
    }
}
