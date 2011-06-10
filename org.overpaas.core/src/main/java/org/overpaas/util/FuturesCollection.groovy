package org.overpaas.util;

import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * a collection of future objects, also treatable as a future over the collection
 * 
 * @author alex
 */
public class FuturesCollection<T> 
 extends ArrayList<Future<T>> 
implements Future<Collection<T>>
//, Collection<Future<T>> 
{
	private static final long serialVersionUID = 1L;

	public FuturesCollection(Future<T> ...values) {
		super(values.length);
		values.each { add(it) }
	}
	
	public FuturesCollection(Collection<Future<T>> collection) {
		super(collection)
	}
	
	/** true if any child was cancelled as a result of this */
	@Override
	public boolean cancel(boolean mayInterruptIfRunning) {
		inject(false) { tally, Future f -> f.cancel(mayInterruptIfRunning) || tally }
	}

	/** true if any child has been cancelled
	 *  TODO: should this be 'every' instead, consistent with isDone() below?
	 */
	@Override
	public boolean isCancelled() {
		any { Future f -> f.isCancelled() }
	}

	/** true if all children are done */
	@Override
	public boolean isDone() {
		every { Future f -> f.isDone() }
	}

	@Override
	public Collection<T> get() throws InterruptedException, ExecutionException {
		collect { Future f -> f.get() }
	}

	@Override
	public Object get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
		def v = Futures.run(collect { Future f -> { -> f.get(timeout, unit) } } )
		v.collect { Future f -> f.get() }
	}
	
}
