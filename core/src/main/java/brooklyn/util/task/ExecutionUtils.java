package brooklyn.util.task;

import groovy.lang.Closure;

import java.util.concurrent.Callable;

import com.google.common.base.Function;
import com.google.common.base.Throwables;

public class ExecutionUtils {
    /**
     * Attempts to run/call the given object, with the given arguments if possible, preserving the return value if there is one (null otherwise);
     * throws exception if the callable is a non-null object which cannot be invoked (not a callable or runnable)
     */
    public static Object invoke(Object callable, Object ...args) {
        if (callable instanceof Closure) return ((Closure<?>)callable).call(args);
        if (callable instanceof Callable) {
            try {
                return ((Callable<?>)callable).call();
            } catch (Throwable t) {
                throw Throwables.propagate(t);
            }
        }
        if (callable instanceof Runnable) { ((Runnable)callable).run(); return null; }
        if (callable instanceof Function && args.length == 1) { return ((Function)callable).apply(args[0]); }
        if (callable==null) return null;
        throw new IllegalArgumentException("Cannot invoke unexpected object "+callable+" of type "+callable.getClass()+", with "+args.length+" args");
    }
}
