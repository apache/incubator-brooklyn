package brooklyn.management.internal.task;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

class DelegateFuture<T> implements Future<T> {

    private final ThreadLocal<Long> threadTimeout = new ThreadLocal<Long>();
    
    private FutureTask<T> target;

    public DelegateFuture() {
    }
    
    public FutureTask<T> getTarget() {
        return target;
    }
    
    public void setTarget(FutureTask<T> val) {
        this.target = val;
    }
    
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
    
    public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        threadTimeout.set(unit.toMillis(timeout));
        try {
            target.run();
            return target.get(timeout, unit);
        } finally {
            threadTimeout.set(null);
        }
    }
}
