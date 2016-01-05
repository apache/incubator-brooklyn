/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.brooklyn.util.time;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import org.apache.brooklyn.util.text.Strings;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;

/** simple class determines a length of time */
public class Duration implements Comparable<Duration>, Serializable {

    private static final long serialVersionUID = -2303909964519279617L;
    
    public static final Duration ZERO = of(0, null);
    public static final Duration ONE_MILLISECOND = of(1, TimeUnit.MILLISECONDS);
    public static final Duration ONE_SECOND = of(1, TimeUnit.SECONDS);
    public static final Duration FIVE_SECONDS = of(5, TimeUnit.SECONDS);
    public static final Duration TEN_SECONDS = of(10, TimeUnit.SECONDS);
    public static final Duration THIRTY_SECONDS = of(30, TimeUnit.SECONDS);
    public static final Duration ONE_MINUTE = of(1, TimeUnit.MINUTES);
    public static final Duration TWO_MINUTES = of(2, TimeUnit.MINUTES);
    public static final Duration FIVE_MINUTES = of(5, TimeUnit.MINUTES);
    public static final Duration ONE_HOUR = of(1, TimeUnit.HOURS);
    public static final Duration ONE_DAY = of(1, TimeUnit.DAYS);
    
    /** longest supported duration, 2^{63}-1 nanoseconds, approx ten billion seconds, or 300 years */ 
    public static final Duration PRACTICALLY_FOREVER = of(Long.MAX_VALUE, TimeUnit.NANOSECONDS);

    private final long nanos;

    public Duration(long value, TimeUnit unit) {
        if (value != 0) {
            Preconditions.checkNotNull(unit, "Cannot accept null timeunit (unless value is 0)");
        } else {
            unit = TimeUnit.MILLISECONDS;
        }
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
        return Long.valueOf(toMilliseconds()).hashCode();
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

    /** 
     * See {@link Time#parseElapsedTime(String)}; 
     * also accepts "forever" (and for those who prefer things exceedingly accurate, "practically_forever"). 
     * Also see {@link #of(Object)}. */
    public static Duration parse(String textualDescription) {
        if (Strings.isBlank(textualDescription)) return null;
        if ("null".equalsIgnoreCase(textualDescription)) return null;
        
        if ("forever".equalsIgnoreCase(textualDescription)) return Duration.PRACTICALLY_FOREVER;
        if ("practicallyforever".equalsIgnoreCase(textualDescription)) return Duration.PRACTICALLY_FOREVER;
        if ("practically_forever".equalsIgnoreCase(textualDescription)) return Duration.PRACTICALLY_FOREVER;
        
        return new Duration((long) Time.parseElapsedTimeAsDouble(textualDescription), TimeUnit.MILLISECONDS);
    }

    /** creates new {@link Duration} instance of the given length of time */
    public static Duration days(Number n) {
        return new Duration((long) (n.doubleValue() * TimeUnit.DAYS.toNanos(1)), TimeUnit.NANOSECONDS);
    }

    /** creates new {@link Duration} instance of the given length of time */
    public static Duration hours(Number n) {
        return new Duration((long) (n.doubleValue() * TimeUnit.HOURS.toNanos(1)), TimeUnit.NANOSECONDS);
    }

    /** creates new {@link Duration} instance of the given length of time */
    public static Duration minutes(Number n) {
        return new Duration((long) (n.doubleValue() * TimeUnit.MINUTES.toNanos(1)), TimeUnit.NANOSECONDS);
    }

    /** creates new {@link Duration} instance of the given length of time */
    public static Duration seconds(Number n) {
        return new Duration((long) (n.doubleValue() * TimeUnit.SECONDS.toNanos(1)), TimeUnit.NANOSECONDS);
    }

    /** creates new {@link Duration} instance of the given length of time */
    public static Duration millis(Number n) {
        return new Duration((long) (n.doubleValue() * TimeUnit.MILLISECONDS.toNanos(1)), TimeUnit.NANOSECONDS);
    }

    /** creates new {@link Duration} instance of the given length of time */
    public static Duration nanos(Number n) {
        return new Duration(n.longValue(), TimeUnit.NANOSECONDS);
    }

    public static Function<Number, String> millisToStringRounded() { return millisToStringRounded; }
    private static Function<Number, String> millisToStringRounded = new Function<Number, String>() {
            @Override
            @Nullable
            public String apply(@Nullable Number input) {
                if (input == null) return null;
                return Duration.millis(input).toStringRounded();
            }
        };

    public static Function<Number, String> secondsToStringRounded() { return secondsToStringRounded; }
    private static Function<Number, String> secondsToStringRounded = new Function<Number, String>() {
            @Override
            @Nullable
            public String apply(@Nullable Number input) {
                if (input == null) return null;
                return Duration.seconds(input).toStringRounded();
            }
        };

    /** tries to convert given object to a Duration, parsing strings, treating numbers as millis, etc;
     * throws IAE if not convertible */
    public static Duration of(Object o) {
        if (o == null) return null;
        if (o instanceof Duration) return (Duration)o;
        if (o instanceof String) return parse((String)o);
        if (o instanceof Number) return millis((Number)o);
        if (o instanceof Stopwatch) return millis(((Stopwatch)o).elapsed(TimeUnit.MILLISECONDS));

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

    public static Duration max(Duration first, Duration second) {
        return checkNotNull(first, "first").nanos >= checkNotNull(second, "second").nanos ? first : second;
    }

    public static Duration min(Duration first, Duration second) {
        return checkNotNull(first, "first").nanos <= checkNotNull(second, "second").nanos ? first : second;
    }

    public static Duration untilUtc(long millisSinceEpoch) {
        return millis(millisSinceEpoch - System.currentTimeMillis());
    }

    public static Duration sinceUtc(long millisSinceEpoch) {
        return millis(System.currentTimeMillis() - millisSinceEpoch);
    }

    public Duration add(Duration other) {
        return nanos(nanos() + other.nanos());
    }

    public Duration subtract(Duration other) {
        return nanos(nanos() - other.nanos());
    }

    public Duration multiply(long x) {
        return nanos(nanos() * x);
    }
    public Duration times(long x) {
        return multiply(x);
    }

    /** as #multiply(long), but approximate due to the division (nano precision) */
    public Duration multiply(double d) {
        return nanos(nanos() * d);
    }

    public Duration half() {
        return multiply(0.5);
    }

    /** see {@link Time#sleep(long)} */
    public static void sleep(Duration duration) {
        Time.sleep(duration);
    }

    /** returns a new started {@link CountdownTimer} with this duration */
    public CountdownTimer countdownTimer() {
        return CountdownTimer.newInstanceStarted(this);
    }

    public boolean isPositive() {
        return nanos()>0;
    }

    public boolean isLongerThan(Duration x) {
        return compareTo(x) > 0;
    }

    public boolean isLongerThan(Stopwatch stopwatch) {
        return isLongerThan(Duration.millis(stopwatch.elapsed(TimeUnit.MILLISECONDS)));
    }

    public boolean isShorterThan(Duration x) {
        return compareTo(x) < 0;
    }

    public boolean isShorterThan(Stopwatch stopwatch) {
        return isShorterThan(Duration.millis(stopwatch.elapsed(TimeUnit.MILLISECONDS)));
    }

    /** returns the larger of this value or the argument */
    public Duration lowerBound(Duration alternateMinimumValue) {
        if (isShorterThan(alternateMinimumValue)) return alternateMinimumValue;
        return this;
    }

    /** returns the smaller of this value or the argument */
    public Duration upperBound(Duration alternateMaximumValue) {
        if (isLongerThan(alternateMaximumValue)) return alternateMaximumValue;
        return this;
    }

    /** @deprecated since 0.7.0 use {@link #lowerBound(Duration)} */ @Deprecated
    public Duration minimum(Duration alternateMinimumValue) {
        return lowerBound(alternateMinimumValue);
    }

    /** @deprecated since 0.7.0 use {@link #upperBound(Duration)} */ @Deprecated
    /** returns the smaller of this value or the argument */
    public Duration maximum(Duration alternateMaximumValue) {
        return upperBound(alternateMaximumValue);
    }
}
