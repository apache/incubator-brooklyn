package brooklyn.util.internal

import groovy.time.TimeDuration

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean;


/**
 * Classloading this class will cause multiply/add to be made available on TimeDuration.
 * For example, I could write: 2*TimeUnit.MINUTES+5*TimeUnit.SECONDS.
 * 
 * That is why nothing seems to use this class, because the methods it defines are not 
 * on this class!
 * 
 * @author alex
 */
class TimeExtras {
    private static AtomicBoolean done = new AtomicBoolean(false);
    
    static void init() {
        if (done.getAndSet(true)) return;   //only run once
        Number.metaClass.multiply << { TimeUnit t -> new TimeDuration(t.toMillis(intValue())) }
        Number.metaClass.multiply << { TimeDuration t -> t.multiply(doubleValue()) }
        
        TimeDuration.metaClass.multiply << { Number n -> new TimeDuration( (int)(toMilliseconds()*n) ) }
        TimeDuration.metaClass.constructor << { long millis ->
            def shift = { int modulus -> int v=millis%modulus; millis/=modulus; v }
            def l = [shift(1000), shift(60), shift(60), shift(24), (int)millis]
            Collections.reverse(l)
            l as TimeDuration
        }
    }
    
    static { init(); }
    
    /** creates a duration object
     * <p>
     * fix for irritating classloading/metaclass order 
     * where an int may get constructed too early and not have the multiply syntax available
     * (or possibly due to invocation from a java class rather than a groovy class?);
     * if e.g. 5*SECONDS throws an error, try duration(5, SECONDS)  */ 
    public static TimeDuration duration(int value, TimeUnit unit) {
        return new TimeDuration(unit.toMillis(value));
    }
}
