package brooklyn.test;

import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;
import groovy.time.TimeDuration;

import java.util.Map;

import com.google.common.annotations.Beta;
import com.google.common.base.Predicate;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableMap;

@Beta
public class Asserts {

    public static <T> void eventually(Supplier<? extends T> supplier, Predicate<T> predicate) {
        eventually(ImmutableMap.<String,Object>of(), supplier, predicate);
    }
    
    public static <T> void eventually(Map<String,?> flags, Supplier<? extends T> supplier, Predicate<T> predicate) {
        eventually(flags, supplier, predicate, (String)null);
    }
    
    public static <T> void eventually(Map<String,?> flags, Supplier<? extends T> supplier, Predicate<T> predicate, String errMsg) {
        TimeDuration timeout = TestUtils.toTimeDuration(flags.get("timeout"), new TimeDuration(0,0,1,0));
        TimeDuration period = TestUtils.toTimeDuration(flags.get("period"), new TimeDuration(0,0,0,10));
        long periodMs = period.toMilliseconds();
        long startTime = System.currentTimeMillis();
        long expireTime = startTime+timeout.toMilliseconds();
        
        boolean first = true;
        T supplied = supplier.get();
        while (first || System.currentTimeMillis() <= expireTime) {
            supplied = supplier.get();
            if (predicate.apply(supplied)) {
                return;
            }
            first = false;
            if (periodMs > 0) sleep(periodMs);
        }
        fail("supplied="+supplied+"; predicate="+predicate+(errMsg!=null?"; "+errMsg:""));
    }
    
    // TODO improve here -- these methods aren't very useful without timeouts
    public static <T> void continually(Supplier<? extends T> supplier, Predicate<T> predicate) {
        continually(ImmutableMap.<String,Object>of(), supplier, predicate);
    }

    public static <T> void continually(Map<String,?> flags, Supplier<? extends T> supplier, Predicate<T> predicate) {
        continually(flags, supplier, predicate, (String)null);
    }

    public static <T> void continually(Map<String,?> flags, Supplier<? extends T> supplier, Predicate<T> predicate, String errMsg) {
        TimeDuration duration = TestUtils.toTimeDuration(flags.get("timeout"), new TimeDuration(0,0,1,0));
        TimeDuration period = TestUtils.toTimeDuration(flags.get("period"), new TimeDuration(0,0,0,10));
        long periodMs = period.toMilliseconds();
        long startTime = System.currentTimeMillis();
        long expireTime = startTime+duration.toMilliseconds();
        
        boolean first = true;
        while (first || System.currentTimeMillis() <= expireTime) {
            assertTrue(predicate.apply(supplier.get()), "supplied="+supplier.get()+"; predicate="+predicate+(errMsg!=null?"; "+errMsg:""));
            if (periodMs > 0) sleep(periodMs);
            first = false;
        }
    }

    private static void sleep(long periodMs) {
        if (periodMs > 0) {
            try {
                Thread.sleep(periodMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }
    }
}
