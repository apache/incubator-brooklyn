package brooklyn.management.internal.task;

import groovy.lang.Closure;

import java.util.concurrent.Future;

/**
 * @deprecated in 0.4; this unused code will be deleted; use guava Future etc where possible 
 */
@Deprecated
public interface QualifiableFuture<T> extends Future<T> {
    Future<T> when(Closure<Boolean> condition);
}
