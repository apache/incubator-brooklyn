package brooklyn.util.task;

import groovy.lang.Closure;

import java.util.concurrent.Callable;

import brooklyn.util.internal.LanguageUtils;

public class ExecutionUtils {

	/** attempts to run/call the given object, with the given arguments if possible, preserving the return value if there is one (null otherwise);
	 * throws exception if the callable is a non-null object which cannot be invoked (not a callable or runnable) */
	public static Object invoke(Object callable, Object ...args) {
		if (callable instanceof Closure) return ((Closure)callable).call(args);
		if (callable instanceof Callable) {
			try {
				return ((Callable)callable).call();
			} catch (Throwable t) {
				return LanguageUtils.throwRuntime(t);
			}
		}
		if (callable instanceof Runnable) { ((Runnable)callable).run(); return null; }
		if (callable==null) return null;
		throw new IllegalArgumentException("Cannot invoke unexpected object "+callable+" of type "+callable.getClass().getCanonicalName());
	}
}
