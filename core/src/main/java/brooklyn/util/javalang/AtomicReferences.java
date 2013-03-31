package brooklyn.util.javalang;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;

public class AtomicReferences {

    /** sets the atomic reference to the given value, and returns whether there is any change */
    public static boolean setIfDifferent(AtomicBoolean ref, boolean value) {
        return ref.getAndSet(value) != value;
    }

    /** sets the atomic reference to the given value, and returns whether there is any change */
    public static <T> boolean setIfDifferent(AtomicReference<T> ref, T value) {
        return ref.getAndSet(value) != value;
    }
    
    /** returns the given atomic as a Supplier */
    public static <T> Supplier<T> supplier(final AtomicReference<T> ref) {
        Preconditions.checkNotNull(ref);
        return new Supplier<T>() {
            @Override public T get() { return ref.get(); }
            @Override public String toString() { return "AtomicRefSupplier"; }
        };
    }
}
