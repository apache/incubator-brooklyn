package brooklyn.test;

import com.google.common.base.Predicate;
import com.google.common.base.Supplier;

public class Asserts {

    public static <T> void eventually(Supplier<? extends T> supplier, Predicate<T> predicate) {
        TestUtils.assertEventually(supplier, predicate);
    }
    
    // TODO improve here -- these methods aren't very useful without timeouts
    public static <T> void continually(Supplier<? extends T> supplier, Predicate<T> predicate) {
        TestUtils.assertContinuallyFromJava(supplier, predicate);
    }

}
