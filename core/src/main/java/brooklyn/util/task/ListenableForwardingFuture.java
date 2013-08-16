package brooklyn.util.task;

import java.util.concurrent.Executor;
import java.util.concurrent.Future;

import com.google.common.util.concurrent.ExecutionList;
import com.google.common.util.concurrent.ForwardingFuture.SimpleForwardingFuture;
import com.google.common.util.concurrent.ListenableFuture;

/** Wraps a Future, making it a ListenableForwardingFuture, but with the caller having the resposibility to:
 * <li> invoke the listeners on job completion (success or error)
 * <li> invoke the listeners on cancel */
public abstract class ListenableForwardingFuture<T> extends SimpleForwardingFuture<T> implements ListenableFuture<T> {

    final ExecutionList listeners;
    
    protected ListenableForwardingFuture(Future<T> delegate) {
        super(delegate);
        this.listeners = new ExecutionList();
    }

    protected ListenableForwardingFuture(Future<T> delegate, ExecutionList list) {
        super(delegate);
        this.listeners = list;
    }

    @Override
    public void addListener(Runnable listener, Executor executor) {
        listeners.add(listener, executor);
    }
    
}
