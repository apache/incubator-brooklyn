package brooklyn.util.exceptions;

import static com.google.common.base.Predicates.instanceOf;
import static com.google.common.base.Throwables.getCausalChain;
import static com.google.common.collect.Iterables.find;

import java.util.NoSuchElementException;
import java.util.concurrent.ExecutionException;

import com.google.common.base.Throwables;

public class Exceptions {

    /**
     * Propagate a {@link Throwable} as a {@link RuntimeException}.
     * <p>
     * Like Guava {@link Throwables#propagate(Throwable)} but throws {@link RuntimeInterruptedException}
     * to handle {@link InterruptedException}s and unpacks the {@link Exception#getCause() cause} and propagates
     * it for {@link ExecutionException}s.
     */
    public static RuntimeException propagate(Throwable throwable) {
        if (throwable instanceof InterruptedException)
            throw new RuntimeInterruptedException((InterruptedException) throwable);
        if (throwable instanceof ExecutionException)
            return Throwables.propagate(throwable.getCause());
        return Throwables.propagate(throwable);
    }

    /** 
     * Propagate exceptions which are fatal.
     * <p>
     * Propagates only those exceptions which one rarely (if ever) wants to capture,
     * such as {@link InterruptedException} and {@link Error}s.
     */
    public static void propagateIfFatal(Throwable throwable) {
        if (throwable instanceof InterruptedException)
            throw new RuntimeInterruptedException((InterruptedException) throwable);
        if (throwable instanceof ExecutionException)
            propagateIfFatal(throwable.getCause());
        if (throwable instanceof Error)
            throw (Error) throwable;
    }

    // based on jclouds Throwables2 (with guice removed)
    @SuppressWarnings("unchecked")
    public static <T extends Throwable> T getFirstThrowableOfType(Throwable from, Class<T> clazz) {
       try {
          return (T) find(getCausalChain(from), instanceOf(clazz));
       } catch (NoSuchElementException e) {
          return null;
       }
    }

}
