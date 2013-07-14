package brooklyn.util.time;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

import com.google.common.base.Preconditions;

/** simple class determines a length of time */
public class Duration implements Comparable<Duration> {

    public static final Duration ONE_SECOND = of(1, TimeUnit.SECONDS);
    public static final Duration FIVE_SECONDS = of(5, TimeUnit.SECONDS);
    public static final Duration TEN_SECONDS = of(10, TimeUnit.SECONDS);
    public static final Duration THIRTY_SECONDS = of(30, TimeUnit.SECONDS);
    public static final Duration ONE_MINUTE = of(1, TimeUnit.MINUTES);
    public static final Duration TWO_MINUTES = of(2, TimeUnit.MINUTES);
    public static final Duration FIVE_MINUTES = of(5, TimeUnit.MINUTES);
    public static final Duration ONE_HOUR = of(1, TimeUnit.HOURS);
    public static final Duration ONE_DAY = of(1, TimeUnit.DAYS);

    
    private final long nanos;

    public Duration(long value, TimeUnit unit) {
        if (value!=0) 
            Preconditions.checkNotNull(unit, "Cannot accept null timeunit (unless value is 0)");
        else
            unit = TimeUnit.MILLISECONDS;
        
        nanos = TimeUnit.NANOSECONDS.convert(value, unit);
    }


    @Override
    public int compareTo(Duration o) {
        return ((Long)toNanoseconds()).compareTo(o.toNanoseconds());
    }

    @Override
    public String toString() {
        return Time.makeTimeStringExact(this);
    }

    public String toStringRounded() {
        return Time.makeTimeStringRounded(this);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Duration)) return false;
        return toMilliseconds() == ((Duration)o).toMilliseconds();
    }
    
    @Override
    public int hashCode() {
        return ((Long)toMilliseconds()).hashCode();
    }

    public long toUnit(TimeUnit unit) {
        return unit.convert(nanos, TimeUnit.NANOSECONDS);
    }
    
    public long toMilliseconds() {
        return toUnit(TimeUnit.MILLISECONDS);
    }
    
    public long toNanoseconds() {
        return nanos;
    }    
    
    public long toSeconds() {
        return toUnit(TimeUnit.SECONDS);
    }
    
    /** number of nanoseconds of this duration */
    public long nanos() {
        return nanos;
    }

    /** see {@link #of(Object)} and {@link Time#parseTimeString(String)} */
    public static Duration parse(String textualDescription) {
        return new Duration(Time.parseTimeString(textualDescription), TimeUnit.MILLISECONDS);
    }

    public static Duration millis(Number n) {
        return new Duration( n.longValue(), TimeUnit.MILLISECONDS );
    }

    public static Duration nanos(Number n) {
        return new Duration( n.longValue(), TimeUnit.NANOSECONDS );
    }
    

    /** tries to convert given object to a Duration, parsing strings, treating numbers as millis, etc;
     * throws IAE if not convertable */
    public static Duration of(Object o) {
        if (o==null) return null;
        if (o instanceof Duration) return (Duration)o;
        if (o instanceof String) return parse((String)o);
        if (o instanceof Number) return millis((Number)o);
        
        try {
            // this allows it to work with groovy TimeDuration
            Method millisMethod = o.getClass().getMethod("toMilliseconds");
            return millis((Long)millisMethod.invoke(o));
        } catch (Exception e) {
            // probably no such method
        }
            
        throw new IllegalArgumentException("Cannot convert "+o+" (type "+o.getClass()+") to a duration");
    }

    public static Duration of(long value, TimeUnit unit) {
        return new Duration(value, unit);
    }
    
    public Duration add(Duration other) {
        return nanos(nanos() + other.nanos());
    }

    public Duration multiply(long x) {
        return nanos(nanos() * x);
    }
    public Duration times(long x) {
        return multiply(x);
    }

    /** see {@link Time#sleep(long)} */
    public static void sleep(Duration duration) {
        Time.sleep(duration);
    }

}
