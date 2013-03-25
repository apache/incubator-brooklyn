package brooklyn.util.javalang;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class AtomicReferences {

    public static boolean setIfDifferent(AtomicBoolean ref, boolean value) {
        return ref.getAndSet(value) != value;
    }

    public static <T> boolean setIfDifferent(AtomicReference<T> ref, T value) {
        return ref.getAndSet(value) != value;
    }

}
