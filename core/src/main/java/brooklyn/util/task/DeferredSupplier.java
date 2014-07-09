package brooklyn.util.task;

import com.google.common.base.Supplier;

/**
 * A class that supplies objects of a single type. When used as a ConfigKey value,
 * the evaluation is deferred until getConfig() is called. The returned value will then
 * be coerced to the correct type. 
 * 
 * Subsequent calls to getConfig will result in further calls to deferredProvider.get(), 
 * rather than reusing the result. If you want to reuse the result, consider instead 
 * using a Future.
 * 
 * Note that this functionality replaces the ues of Closure in brooklyn 0.4.0, which 
 * served the same purpose.
 */
public interface DeferredSupplier<T> extends Supplier<T> {
    @Override
    T get();
}
