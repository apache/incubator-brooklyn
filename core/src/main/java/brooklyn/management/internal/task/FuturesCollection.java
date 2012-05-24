package brooklyn.management.internal.task;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.google.common.util.concurrent.JdkFutureAdapters;
import com.google.common.util.concurrent.ListenableFuture;

/**
 * A collection of future objects, also treatable as a future over the collection.
 * 
 * @deprecated in 0.4; this unused code will be deleted; use guava Future etc where possible 
 * @author alex
 */
@Deprecated
public class FuturesCollection extends ArrayList<Future<?>> implements Future<Collection<?>> {
    private static final long serialVersionUID = 1L;

    public FuturesCollection(Future...values) {
        super(values.length);
        for (Future it : values) {
            add(it);
        }
    }
    
    public FuturesCollection(Collection<Future<?>> collection) {
        super(collection);
    }
    
    /**
     * True if any child was cancelled as a result of this
     */
    public boolean cancel(boolean mayInterruptIfRunning) {
        boolean tally = false;
        for (Future<?> f : this) {
            tally = f.cancel(mayInterruptIfRunning) || tally;
        }
        return tally;
    }

    /**
     * True if any child has been cancelled
     * TODO: should this be 'every' instead, consistent with isDone() below?
     */
    public boolean isCancelled() {
        for (Future<?> f : this) {
            if (f.isCancelled()) return true;
        }
        return false;
    }

    /** true if all children are done */
    public boolean isDone() {
        boolean result = true;
        for (Future<?> f : this) {
            result = result && f.isDone();
        }
        return result;
    }

    public Collection get() throws InterruptedException, ExecutionException {
        List<Object> result = new ArrayList<Object>(this.size());
        for (Future<?> f : this) {
            result.add(f.get());
        }
        return result;
    }

    public Collection get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        List<ListenableFuture<?>> listenableFutures = new ArrayList<ListenableFuture<?>>(this.size());
        
        for (Future<?> f : this) {
            listenableFutures.add(JdkFutureAdapters.listenInPoolThread(f));
        }
        ListenableFuture<List<Object>> compound = com.google.common.util.concurrent.Futures.allAsList(listenableFutures);
        return compound.get(timeout, unit);
    }
}
