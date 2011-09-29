package brooklyn.management.internal.task

import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit

class DelegateFuture<T> implements Future<T> {
    FutureTask<T> target;

    public boolean cancel(boolean mayInterruptIfRunning) {
        return target.cancel(mayInterruptIfRunning);
    }

    public boolean isCancelled() {
        return target.isCancelled();
    }

    public boolean isDone() {
        target.run();
        return target.isDone();
    }

    public T get() throws InterruptedException, ExecutionException {
        target.run();
        return target.get();
    }
    
    ThreadLocal threadTimeout = new ThreadLocal();
    
    public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException {
        threadTimeout.set(unit.toMillis(timeout))
        try {
            target.run();
            return target.get(timeout, unit);
        } finally {
            threadTimeout.set(null)
        }
    }
}
