package org.overpaas.util.concurrent

import groovy.lang.Closure;
import groovy.time.TimeDuration;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

class DelegatingExecutor implements Executor {
	ExecutorService executor = Executors.newCachedThreadPool();
	TimeDuration timeout = null;
	Closure preTask=null, postTask=null;
	
	public void execute(Runnable command) {
		executor.execute {
			try {
				preTask?.run()
				command.run()
			} finally {
				postTask?.run()
			}
		}
	}
	
	public void shutdown() {
		executor.shutdown()
	}
 
	public void shutdownNow() {
		executor.shutdownNow()
	}

	public void preTask() {}
	public void postTask() {}
		
	public <T> Callable<T> asWrappedCallable(final Closure<T> c) {
		return new Callable() {
			public Object call() throws Exception {
				try {
					preTask?.run()
					return c.run()
				} finally { postTask?.run() } 
			}
		};
	}
	
	public <T> List<Future<T>> executeAll(final Closure<T>...c) {
		if (timeout)
            new FuturesCollection<T>(executor.invokeAll(c.collect { asWrappedCallable(it) }, timeout.toMilliseconds(), TimeUnit.MILLISECONDS));
		else
            new FuturesCollection<T>(executor.invokeAll(c.collect { asWrappedCallable(it) }))
	}
}
