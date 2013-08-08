package brooklyn.util.task;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.management.Task;

/**
 * Instances of this class ensures that {@link Task}s execute with in-order
 * single-threaded semantics.
 *
 * Tasks can be presented through {@link #submit(Callable)}. The order of execution is the
 * sumbission order.
 * <p>
 * This implementation does so by blocking on a {@link ConcurrentLinkedQueue}, <em>after</em>
 * the task is started in a thread (and {@link Task#isBegun()} returns true), but (of course)
 * <em>before</em> the {@link BasicTask#job} actually gets invoked.
 */
public class SingleThreadedScheduler implements TaskScheduler, CanSetName {
    private static final Logger LOG = LoggerFactory.getLogger(SingleThreadedScheduler.class);
    
    private final Queue<QueuedSubmission<?>> order = new ConcurrentLinkedQueue<QueuedSubmission<?>>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    
    private ExecutorService executor;

    private String name;
    
    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name!=null ? "SingleThreadedExecutor["+name+"]" : super.toString();
    }
    
    @Override
    public void injectExecutor(ExecutorService executor) {
        this.executor = executor;
    }

    @Override
    public synchronized <T> Future<T> submit(Callable<T> c) {
        if (running.compareAndSet(false, true)) {
            return executeNow(c);
        } else {
            WrappingFuture<T> f = new WrappingFuture<T>();
            order.add(new QueuedSubmission<T>(c, f));
            int size = order.size();
            if (size>0 && (size == 10 || (size<=500 && (size%100)==0) || (size%1000)==0) && size!=lastSizeWarn) {
                LOG.warn("{} is backing up, {} tasks queued", this, size);
                lastSizeWarn = size;
            }
            return f;
        }
    }
    int lastSizeWarn = 0;

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private synchronized void onEnd() {
        boolean done = false;
        while (!done) {
            if (order.isEmpty()) {
                running.set(false);
                done = true;
            } else {
                QueuedSubmission<?> qs = order.remove();
                if (!qs.f.isCancelled()) {
                    Future future = executeNow(qs.c);
                    qs.f.setDelegate(future);
                    done = true;
                }
            }
        }
    }

    private synchronized <T> Future<T> executeNow(final Callable<T> c) {
        return executor.submit(new Callable<T>() {
            @Override public T call() throws Exception {
                try {
                    return c.call();
                } finally {
                    onEnd();
                }
            }});
    }
    
    
    private static class QueuedSubmission<T> {
        final Callable<T> c;
        final WrappingFuture<T> f;
        
        QueuedSubmission(Callable<T> c, WrappingFuture<T> f) {
            this.c = c;
            this.f = f;
        }
    }
    
    /**
     * A future, where the task may not yet have been submitted to the real executor.
     * It delegates to the real future if present, and otherwise waits for that to appear
     */
    private static class WrappingFuture<T> implements Future<T> {
        private volatile Future<T> delegate;
        private boolean cancelled;
        
        void setDelegate(Future<T> delegate) {
            synchronized (this) {
                this.delegate = delegate;
                notifyAll();
            }
        }
        
        @Override public boolean cancel(boolean mayInterruptIfRunning) {
            if (delegate != null) {
                return delegate.cancel(mayInterruptIfRunning);
            } else {
                cancelled = true;
                synchronized (this) {
                    notifyAll();
                }
                return true;
            }
        }
        
        @Override public boolean isCancelled() {
            if (delegate != null) {
                return delegate.isCancelled();
            } else {
                return cancelled;
            }
        }
        
        @Override public boolean isDone() {
            return (delegate != null) ? delegate.isDone() : cancelled;
        }
        
        @Override public T get() throws CancellationException, ExecutionException, InterruptedException {
            if (cancelled) {
                throw new CancellationException();
            } else if (delegate != null) {
                return delegate.get();
            } else {
                synchronized (this) {
                    while (delegate == null && !cancelled) {
                        wait();
                    }
                }
                return get();
            }
        }
        
        @Override public T get(long timeout, TimeUnit unit) throws CancellationException, ExecutionException, InterruptedException, TimeoutException {
            long endtime = System.currentTimeMillis()+unit.toMillis(timeout);
            
            if (cancelled) {
                throw new CancellationException();
            } else if (delegate != null) {
                return delegate.get(timeout, unit);
            } else if (System.currentTimeMillis() >= endtime) {
                throw new TimeoutException();
            } else {
                synchronized (this) {
                    while (delegate == null && !cancelled && System.currentTimeMillis() < endtime) {
                        long remaining = endtime - System.currentTimeMillis();
                        if (remaining > 0) {
                            wait(remaining);
                        }
                    }
                }
                long remaining = endtime - System.currentTimeMillis();
                return get(remaining, TimeUnit.MILLISECONDS);
            }
        }
    }
}
