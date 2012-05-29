package brooklyn.util;

import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import com.google.common.base.Function
import com.google.common.base.Predicate

/** handy methods available in groovy packaged so they can be consumed from java,
 *  and other conversion/conveniences; but see JavaGroovyEquivalents for faster alternatives */
public class GroovyJavaMethods {

    //TODO use named subclasses, would that be more efficient?
    
    public static Closure closureFromRunnable(final Runnable job) {
        return { it ->
            if (job in Callable) { job.call() }
            else { job.run(); null; }
        };
    }
    
    public static Closure closureFromCallable(final Callable job) {
        return { it -> job.call(); };
    }

    public static <T> Callable<T> callableFromClosure(final Closure<T> job) {
        return job as Callable;
    }

    public static <T> Callable<T> callableFromRunnable(final Runnable job) {
        return (job in Callable) ? callableFromClosure(job) : Executors.callable(job);
    }

    public static <K,T> Function<K,T> functionFromClosure(final Closure<T> job) {
        return job as Function;
    }

    public static <T> Predicate<T> predicateFromClosure(final Closure<Boolean> job) {
        return job as Predicate;
    }
    
    public static boolean truth(Object o) {
        if (o) return true;
        return false;
    }

    public static <T> T elvis(Object preferred, Object fallback) {
        return fix(preferred ?: fallback);
    }
    
    public static <T> T elvis(Object... preferences) {
        if (preferences.length == 0) throw new IllegalArgumentException("preferences must not be empty for elvis");
        for (Object contender : preferences) {
            if (contender) return fix(contender);
        }
        return fix(preferences[preferences.size()-1]);
    }
    
    public static <T> T fix(Object o) {
        if (o in GString) return (o as String);
        return o;
    }
}
