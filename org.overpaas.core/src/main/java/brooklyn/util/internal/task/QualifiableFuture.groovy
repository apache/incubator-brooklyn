package brooklyn.util.internal.task;

import groovy.lang.Closure;

import java.util.concurrent.Future;

public interface QualifiableFuture<T> extends Future<T> {
	Future<T> when(Closure condition);
}
