package brooklyn.util.exceptions;

import com.google.common.base.Throwables;

public class Exceptions {

    /** like guava {@link Throwables#propagate(Throwable)}, 
     * but set interrupted if interrupted exception (why doesn't guava do this?!), 
     * and throw {@link RuntimeInterruptedException} */
    public static RuntimeException propagate(Throwable throwable) {
        if (throwable instanceof InterruptedException)
            throw new RuntimeInterruptedException((InterruptedException)throwable);
        return Throwables.propagate(throwable);
    }

    /** propagates exceptions which are fatal, ie those which one rarely if ever wants to capture
     * (such as InterruptedException and Errors) */
    public static void propagateIfFatal(Throwable throwable) {
        if (throwable instanceof InterruptedException)
            throw new RuntimeInterruptedException((InterruptedException)throwable);
        if (throwable instanceof Error)
            throw (Error) throwable;
    }

}
