package brooklyn.util.internal

import groovy.time.TimeDuration

import java.util.concurrent.TimeUnit

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.util.Time


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
    public static final Logger log = LoggerFactory.getLogger(TimeExtras.class);
    
    public static void init() {
        Number.metaClass.multiply << { TimeUnit t -> new TimeDuration(t.toMillis(intValue())) }
        Number.metaClass.multiply << { TimeDuration t -> t.multiply(doubleValue()) }
        Integer.metaClass.multiply << { TimeUnit t -> new TimeDuration(t.toMillis(intValue())) }
        
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
     * (because grail is invoked?; if e.g. 5*SECONDS throws an error, try duration(5, SECONDS)  */ 
    public static TimeDuration duration(int value, TimeUnit unit) {
        return new TimeDuration(0, 0, 0, (int)unit.toMillis(value));
    }
    
    public static final TimeDuration ONE_SECOND = duration(1, TimeUnit.SECONDS);
    public static final TimeDuration FIVE_SECONDS = duration(5, TimeUnit.SECONDS);
    public static final TimeDuration TEN_SECONDS = duration(10, TimeUnit.SECONDS);
    public static final TimeDuration THIRTY_SECONDS = duration(30, TimeUnit.SECONDS);
    public static final TimeDuration ONE_MINUTE = duration(1, TimeUnit.MINUTES);
    public static final TimeDuration TWO_MINUTES = duration(2, TimeUnit.MINUTES);
    public static final TimeDuration FIVE_MINUTES = duration(5, TimeUnit.MINUTES);

    public static void sleep(TimeDuration duration) {
        Time.sleep(duration.toMilliseconds());
    }    
    
}
