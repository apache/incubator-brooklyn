package brooklyn.util.exceptions;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Predicates.instanceOf;
import static com.google.common.base.Throwables.getCausalChain;

import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.ExecutionException;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Throwables;
import com.google.common.collect.Iterables;

public class Exceptions {

    @SuppressWarnings("unchecked")
    private static final Predicate<Object> IS_THROWABLE_BORING = Predicates.<Object>or(
            instanceOf(ExecutionException.class),
            instanceOf(InvocationTargetException.class),
            instanceOf(PropagatedRuntimeException.class)
        );

    /**
     * Propagate a {@link Throwable} as a {@link RuntimeException}.
     * <p>
     * Like Guava {@link Throwables#propagate(Throwable)} but:
     * <li> throws {@link RuntimeInterruptedException} to handle {@link InterruptedException}s; and
     * <li> wraps as PropagatedRuntimeException for easier filtering
     */
    public static RuntimeException propagate(Throwable throwable) {
        if (throwable instanceof InterruptedException)
            throw new RuntimeInterruptedException((InterruptedException) throwable);
        Throwables.propagateIfPossible(checkNotNull(throwable));
        throw new PropagatedRuntimeException(throwable);
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
        if (throwable instanceof Error)
            throw (Error) throwable;
    }

    /** returns the first exception of the given type, or null */
    @SuppressWarnings("unchecked")
    public static <T extends Throwable> T getFirstThrowableOfType(Throwable from, Class<T> clazz) {
        return (T) Iterables.tryFind(getCausalChain(from), instanceOf(clazz)).orNull();
    }

    /** returns the first exception in the call chain which is not of common uninteresting types
     * (ie excluding ExecutionException and PropagatedRuntimeExceptions); 
     * or the original throwable if all are uninteresting 
     */
    public static Throwable getFirstInteresting(Throwable throwable) {
        return Iterables.tryFind(getCausalChain(throwable), Predicates.not(IS_THROWABLE_BORING)).or(throwable);
    }

    /** as {@link #propagateCollapsed(Throwable)} but does not throw */
    public static Throwable collapse(Throwable source) {
        String message = "";
        Throwable collapsed = source;
        int collapseCount = 0;
        while (IS_THROWABLE_BORING.apply(collapsed)) {
            collapseCount++;
            Throwable cause = collapsed.getCause();
            if (cause==null)
                // everything in the tree is boring
                return source;
            if (collapsed.getMessage()!=null) {
                // prevents repeated propagation from embedding endless toStrings
                String collapsedS = collapsed.getMessage();
                String causeM = cause.toString();
                if (collapsedS.endsWith(causeM))
                    collapsedS = collapsedS.substring(0, collapsedS.length()-causeM.length());
                if (message.length()>0 && collapsedS.length()>0) message += ": ";
                message += collapsedS;
            }
            collapsed = cause;
        }
        if (collapseCount==0)
            return source;
        if (message.length()==0)
            return new PropagatedRuntimeException(collapsed);
        else
            return new PropagatedRuntimeException(message, collapsed);
    }

    /** removes uninteresting items from the top of the call stack (but keeps interesting messages), and throws */
    public static RuntimeException propagateCollapsed(Throwable source) {
        throw propagate(source);
    }

    /** like {@link #collapse(Throwable)} but returning a one-line message suitable for logging without traces */
    public static String collapseText(Throwable t) {
        if (t == null) return null;
        Throwable t2 = collapse(t);
        if (t2 instanceof PropagatedRuntimeException) {
            if (t2.getMessage()!=null && t2.getMessage().length()>0) 
                return t2.getMessage() + ": "+collapseText(t2.getCause());
            else
                return collapseText(t2.getCause());
        }
        return t2.toString();
    }

}
