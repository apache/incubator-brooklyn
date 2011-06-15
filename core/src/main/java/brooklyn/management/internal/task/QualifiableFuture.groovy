package brooklyn.management.internal.task;

import java.util.concurrent.Future


public interface QualifiableFuture<T> extends Future<T> {
	Future<T> when(Closure condition);
}
