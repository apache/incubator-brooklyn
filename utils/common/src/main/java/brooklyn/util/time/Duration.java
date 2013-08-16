package brooklyn.util.time;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.TimeUnit;

import com.google.common.base.Preconditions;

/** simple class determines a length of time */
public class Duration implements Comparable<Duration> {

    public static final Duration ZERO = of(0, null);
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

    /** converts to the given {@link TimeUnit}, using {@link TimeUnit#convert(long, TimeUnit)} which rounds _down_
     * (so 1 nanosecond converted to milliseconds gives 0 milliseconds, and -1 ns gives -1 ms) */
    public long toUnit(TimeUnit unit) {
        return unit.convert(nanos, TimeUnit.NANOSECONDS);
    }

    /** as {@link #toUnit(TimeUnit)} but rounding as indicated
     * (rather than always taking the floor which is TimeUnit's default behaviour) */
    public long toUnit(TimeUnit unit, RoundingMode rounding) {
        long result = unit.convert(nanos, TimeUnit.NANOSECONDS);
        long check = TimeUnit.NANOSECONDS.convert(result, unit);
        if (check==nanos || rounding==null || rounding==RoundingMode.UNNECESSARY) return result;
        return new BigDecimal(nanos).divide(new BigDecimal(unit.toNanos(1)), rounding).longValue();
    }

    /** as {@link #toUnit(TimeUnit)} but rounding away from zero,
     * so 1 ns converted to ms gives 1 ms, and -1 ns gives 1ms */
    public long toUnitRoundingUp(TimeUnit unit) {
        return toUnit(unit, RoundingMode.UP);
    }

    public long toMilliseconds() {
        return toUnit(TimeUnit.MILLISECONDS);
    }
    
    /** as {@link #toMilliseconds()} but rounding away from zero (so 1 nanosecond gets rounded to 1 millisecond);
     * see {@link #toUnitRoundingUp(TimeUnit)}; provided as a convenience on top of {@link #toUnit(TimeUnit, RoundingMode)}
     * as this is a common case (when you want to make sure you wait at least a certain amount of time) */
    public long toMillisecondsRoundingUp() {
        return toUnitRoundingUp(TimeUnit.MILLISECONDS);
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
