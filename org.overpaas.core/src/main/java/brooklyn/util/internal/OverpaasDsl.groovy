package brooklyn.util.internal;

import groovy.lang.Closure

import java.util.List
import java.util.Map
import java.util.concurrent.Future

import brooklyn.util.internal.task.Futures
import brooklyn.util.internal.task.QualifiableFuture

/**
 * Class whose statics can be imported to make code more readable, viz.
 * 
 * import brooklyn.util.internal.OverpaasDsl.*
 * 
 * @author alex
 */
public class OverpaasDsl {
	static {
		Map.metaClass.submap = { String ...values -> submap(Arrays.asList(values)) }
		Map.metaClass.submap = { List values ->
			//TODO return a submap which will also understand futures
			null
		}
		
		TimeExtras.init()
		
		Object.metaClass.getPropertySafe = { String name -> hasProperty(name)?.getProperty(this) }
	}
	
	static Object file(String name) {
		// TODO
		null
	}
	
	/** runs the given closures simultaneously; can optionally be give a timeout (in TimeDuration) and/or an ExecutorService executor */
	static List<Future<?>> run(Map m=[:], Closure ...c) { Futures.run(m, c) }
 
	/** waits for the indicated futures to complete; throws single aggregate ExecutionError if there are one or more errors */
	static List<Future<?>> waitFor(List<Future<?>> futures) { Futures.waitFor(futures) }
 
	/** returns the value when it is truthy (ie non-null, non-zero, non-empty) */
	static <T> QualifiableFuture<T> futureValue(Closure value) { Futures.futureValue value }
 
	/** returns the value when isValueReady evaluates to true */
	static <T> Future<T> futureValueWhen(Closure value, Closure isValueReady) { Futures.futureValueWhen value, isValueReady }
 
	/** returns the value when it is not empty (or has groovy truth) */
	static <T> Future<T> futureValueWhenNonEmpty(Closure value) { Futures.futureValueWhen value }
 
	/** returns the given value; when the item is a future, it waits for the future to be done */
	static Object getBlocking(Object v) { Futures.getBlocking(v) }
}
