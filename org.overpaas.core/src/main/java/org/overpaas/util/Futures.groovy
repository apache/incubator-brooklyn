package org.overpaas.util


import groovy.lang.Closure;
import groovy.time.TimeDuration;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException 
import java.util.concurrent.ExecutionException 
import java.util.concurrent.Future 
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;


public class Futures {
	
	private Futures() {}
	
	//	public static class DelegateFuture<T> implements Future<T> {
	//		FutureTask<T> target;
	//		
	//		public boolean cancel(boolean mayInterruptIfRunning) {
	//			return target.cancel(mayInterruptIfRunning);
	//		}
	//		
	//		public boolean isCancelled() {
	//			return target.isCancelled();
	//		}
	//		
	//		public boolean isDone() {
	//			target.run();
	//			return target.isDone();
	//		}
	//		
	//		public T get() throws InterruptedException, ExecutionException {
	//			target.run();
	//			return target.get();
	//		}
	//		
	//		static { TimeExtras.init() }
	//		ThreadLocal threadTimeout = new ThreadLocal();
	//		public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException {
	//			threadTimeout.set(timeout*unit)
	//			try {
	//				target.run();
	//				return target.get(timeout, unit);
	//			} finally { threadTimeout.set(null) }
	//		}
	//	}
	//	
	//	public static class QualifiableFutureImpl<T> extends DelegateFuture<T> implements QualifiableFuture<T> {
	//		/** creates a future which will return the value of expression when that value satisfies validity; it will continually attempt to evaluate 'value' */
	//		public QualifiableFutureImpl(Closure expression, Closure validity = { it as Boolean }) {
	//			this.expression = expression;
	//			this.validity = validity;
	//			target = new FutureTask(new Callable<T>() {
	//						public T call() throws Exception {
	//							TimeDuration t = QualifiableFutureImpl.this.threadTimeout.get()
	//							long startTime = System.currentTimeMillis();
	//							long endTime = t!=null && t>0 ? System.currentTimeMillis() + t.toMilliseconds() : -1;
	//							long nextTime = startTime;
	//							while (nextTime>0) {
	//								if (iters++>0) onNotValid(endTime - System.currentTimeMillis());
	//								T result = getExpression().call();
	//								println "called expression "+getExpression()+", got "+result+", validity "+isValid(result)
	//								if (isValid(result)) return result;
	//							}
	//							throw new CancellationException()
	//						}
	//					} );
	//		}
	//		Closure expression, validity;
	//		public boolean isValid(T result) {
	//			if (!validity) true
	//			else validity.call(result)
	//		}
	//		public void onNotValid(long remainingMillis) throws InterruptedException {
	//			if (remainingMillis<)
	//			Thread.sleep(100)
	//		}
	//		public Future<T> when(Closure condition) {
	//			return new QualifiableFutureImpl(expression, { isValid(it) && condition.call(it) })
	//		}
	//	}
	
	
	/** runs the given closures simultaneously; can optionally be give a timeout (in TimeDuration, after which jobs are cancelled) and/or an ExecutorService executor */
	//TODO rather than cancel after timeout (the default), we could simply apply the timeout to waitFor
	static FuturesCollection run(Map m=[:], Closure ...c) {
		List<Future> result = start(m, c)
		waitFor(result);
		result
	}
	static FuturesCollection start(Map m=[:], Closure ...c) {
		Map m2 = new LinkedHashMap(m);
		DelegatingExecutor ex = new DelegatingExecutor(m2);
		List<Future> result = ex.executeAll(c)
		ex.shutdown()
		result
	}
	
	static FuturesCollection waitFor(Collection<Future<?>> futures) {
		def problems = []
		futures.each { try { it.get() } catch (Exception e) { if (!(e in TimeoutException) && !(e in CancellationException)) problems += e } }
		if (problems) {
			throw problems.size()==1 ? new ExecutionException((String)problems[0].message, problems[0]) :
				new ExecutionException(""+problems.size()+" errors, including: "+problems[0], problems[0])
		}
		futures
	}
	
	/** returns the value when it is truthy (ie non-null, non-zero, non-empty) */
	static <T> QualifiableFuture<T> futureValue(Closure value) {
		futureValueWhen value
	}
	
	/** returns the value when isValueReady evaluates to true */
	static <T> Future<T> futureValueWhen(Closure value, Closure isValueReady = { it }) {
//		println "defining future value when "+value+", "+isValueReady+"."
		return new FutureValue<Object>(value, isValueReady);
		//		 {
		//			public void onNotValid() {
		//				println "waiting for "+value //+" ("+JPaasProvisioningContext.contextMessage+")"
		//			}
		//		}
	}
	
	/** returns the given value; when the item is a future, it waits for the future to be done */
	static Object getBlocking(Map params=[:], Object v) {
		if (v in Future) {
			try {
				TimeDuration t = params["timeout"]
				if (t in TimeDuration)
					return v.get(params["timeout"].toMilliseconds(), TimeUnit.MILLISECONDS)
				else return v.get()
			} catch (Exception e) {
				if ((e in TimeoutException) && params["defaultValue"]) return params["defaultValue"] 
				throw e;
			}
		}
		return v
	}
	
	
	
	public static void main(String[] args) {
		println ""+System.currentTimeMillis()+" starting 1"
		def qfi1 = new FutureValue({ 1 }, { true });
		QualifiableFuture<Object> qf1 = qfi1;
		long e0 = System.currentTimeMillis()
		def v1 = getBlocking(qf1)
		long e1 = System.currentTimeMillis()
		println ""+System.currentTimeMillis()+" "+v1+" ("+(e1-e0)+")"
		
		println ""+System.currentTimeMillis()+" starting 2"
		def qfi2 = qfi1.when { false }
		new Thread( { def v2 = getBlocking(qfi2, timeout:1*TimeUnit.SECONDS, defaultValue:-1); println ""+System.currentTimeMillis()+" ALT "+v2 } ).start()
		def v2 = getBlocking(qfi2, timeout:1*TimeUnit.SECONDS, defaultValue:-1)
		println ""+System.currentTimeMillis()+" "+v2
		
		println ""+System.currentTimeMillis()+" starting 3"
		def x = [ 0 ]
		def qfi3 = new FutureValue({ x[0] });
		new Thread( { def v3 = getBlocking(qfi3, timeout:1*TimeUnit.SECONDS, defaultValue:-1); println ""+System.currentTimeMillis()+" ALT "+v3 } ).start()
		new Thread( { def v3 = getBlocking(qfi3, timeout:2*TimeUnit.SECONDS, defaultValue:-1); println ""+System.currentTimeMillis()+" ALT2 "+v3 } ).start()
		new Thread( { Thread.sleep(1500); x[0] = 3; } ).start()
		def v3 = getBlocking(qfi3, timeout:2*TimeUnit.SECONDS, defaultValue:-1)
		println ""+System.currentTimeMillis()+" "+v3
		// TODO test, above should finishes in < 2s !
		
	}
	
}

