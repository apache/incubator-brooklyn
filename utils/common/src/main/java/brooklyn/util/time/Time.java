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
package brooklyn.util.time;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;
import java.util.SimpleTimeZone;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.util.collections.MutableList;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.guava.Maybe;
import brooklyn.util.text.Strings;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;

public class Time {

    private static final Logger log = LoggerFactory.getLogger(Time.class);
    
    public static final String DATE_FORMAT_PREFERRED_W_TZ = "yyyy-MM-dd HH:mm:ss.SSS Z";
    public static final String DATE_FORMAT_PREFERRED = "yyyy-MM-dd HH:mm:ss.SSS";
    public static final String DATE_FORMAT_STAMP = "yyyyMMdd-HHmmssSSS";
    public static final String DATE_FORMAT_SIMPLE_STAMP = "yyyy-MM-dd-HHmm";
    public static final String DATE_FORMAT_OF_DATE_TOSTRING = "EEE MMM dd HH:mm:ss zzz yyyy";
    public static final String DATE_FORMAT_ISO8601 = "yyyy-MM-dd'T'HH:mm:ss.SSSZ";
    public static final String DATE_FORMAT_ISO8601_NO_MILLIS = "yyyy-MM-dd'T'HH:mm:ssZ";

    public static final long MILLIS_IN_SECOND = 1000;
    public static final long MILLIS_IN_MINUTE = 60*MILLIS_IN_SECOND;
    public static final long MILLIS_IN_HOUR = 60*MILLIS_IN_MINUTE;
    public static final long MILLIS_IN_DAY = 24*MILLIS_IN_HOUR;
    public static final long MILLIS_IN_YEAR = 365*MILLIS_IN_DAY;
    
    /** GMT/UTC/Z time zone constant */
    public static final TimeZone TIME_ZONE_UTC = TimeZone.getTimeZone("");
    
    /** as {@link #makeDateString(Date)} for current date/time */
    public static String makeDateString() {
        return makeDateString(System.currentTimeMillis());
    }

    /** as {@link #makeDateString(Date)} for long millis since UTC epock */
    public static String makeDateString(long date) {
        return makeDateString(new Date(date), DATE_FORMAT_PREFERRED);
    }
    /** returns the time in {@value #DATE_FORMAT_PREFERRED} format for the given date;
     * this format is numeric big-endian but otherwise optimized for people to read, with spaces and colons and dots;
     * time is local to the server and time zone is <i>not</i> included */
    public static String makeDateString(Date date) {
        return makeDateString(date, DATE_FORMAT_PREFERRED);
    }
    /** as {@link #makeDateString(Date, String, TimeZone)} for the local time zone */
    public static String makeDateString(Date date, String format) {
        return makeDateString(date, format, null);
    }
    /** as {@link #makeDateString(Date, String, TimeZone)} for the given time zone; consider {@link TimeZone#GMT} */
    public static String makeDateString(Date date, String format, TimeZone tz) {
        SimpleDateFormat fmt = new SimpleDateFormat(format);
        if (tz!=null) fmt.setTimeZone(tz);
        return fmt.format(date);
    }
    /** as {@link #makeDateString(Date, String)} using {@link #DATE_FORMAT_PREFERRED_W_TZ} */
    public static String makeDateString(Calendar date) {
        return makeDateString(date.getTime(), DATE_FORMAT_PREFERRED_W_TZ);
    }
    /** as {@link #makeDateString(Date, String, TimeZone)} for the time zone of the given calendar object */
    public static String makeDateString(Calendar date, String format) {
        return makeDateString(date.getTime(), format, date.getTimeZone());
    }

    public static Function<Long, String> toDateString() { return dateString; }
    private static Function<Long, String> dateString = new Function<Long, String>() {
            @Override
            @Nullable
            public String apply(@Nullable Long input) {
                if (input == null) return null;
                return Time.makeDateString(input);
            }
        };

    /** returns the current time in {@value #DATE_FORMAT_STAMP} format,
     * suitable for machines to read with only numbers and dashes and quite precise (ms) */
    public static String makeDateStampString() {
        return makeDateStampString(System.currentTimeMillis());
    }

    /** returns the time in {@value #DATE_FORMAT_STAMP} format, given a long (e.g. returned by System.currentTimeMillis);
     * cf {@link #makeDateStampString()} */
    public static String makeDateStampString(long date) {
        return new SimpleDateFormat(DATE_FORMAT_STAMP).format(new Date(date));
    }

    /** returns the current time in {@value #DATE_FORMAT_SIMPLE_STAMP} format, 
     * suitable for machines to read but easier for humans too, 
     * like {@link #makeDateStampString()} but not as precise */
    public static String makeDateSimpleStampString() {
        return makeDateSimpleStampString(System.currentTimeMillis());
    }

    /** returns the time in {@value #DATE_FORMAT_SIMPLE_STAMP} format, given a long (e.g. returned by System.currentTimeMillis);
     * cf {@link #makeDateSimpleStampString()} */
    public static String makeDateSimpleStampString(long date) {
        return new SimpleDateFormat(DATE_FORMAT_SIMPLE_STAMP).format(new Date(date));
    }

    public static Function<Long, String> toDateStampString() { return dateStampString; }
    private static Function<Long, String> dateStampString = new Function<Long, String>() {
            @Override
            @Nullable
            public String apply(@Nullable Long input) {
                if (input == null) return null;
                return Time.makeDateStampString(input);
            }
        };

    /** @see #makeTimeString(long, boolean) */
    public static String makeTimeStringExact(long t, TimeUnit unit) {
        long nanos = unit.toNanos(t);
        return makeTimeStringNanoExact(nanos);
    }
    /** @see #makeTimeString(long, boolean) */
    public static String makeTimeStringRounded(long t, TimeUnit unit) {
        long nanos = unit.toNanos(t);
        return makeTimeStringNanoRounded(nanos);
    }
    public static String makeTimeStringRounded(Stopwatch timer) {
        return makeTimeStringRounded(timer.elapsed(TimeUnit.MILLISECONDS), TimeUnit.MILLISECONDS);
    }
    /** @see #makeTimeString(long, boolean) */
    public static String makeTimeStringExact(long t) {
        return makeTimeString(t, false);
    }
    /** @see #makeTimeString(long, boolean) */
    public static String makeTimeStringRounded(long t) {
        return makeTimeString(t, true);
    }
    /** @see #makeTimeString(long, boolean) */
    public static String makeTimeStringRoundedSince(long utc) {
        return makeTimeString(System.currentTimeMillis() - utc, true);
    }
    /** @see #makeTimeString(long, boolean) */
    public static String makeTimeStringExact(Duration d) {
        return makeTimeStringNanoExact(d.toNanoseconds());
    }
    /** @see #makeTimeString(long, boolean) */
    public static String makeTimeStringRounded(Duration d) {
        return makeTimeStringNanoRounded(d.toNanoseconds());
    }
    /** given an elapsed time, makes it readable, eg 44d 6h, or 8s 923ms, optionally rounding */
    public static String makeTimeString(long t, boolean round) {
        return makeTimeStringNano(t*1000000L, round);
    }
    /** @see #makeTimeString(long, boolean) */
    public static String makeTimeStringNanoExact(long tn) {
        return makeTimeStringNano(tn, false);
    }
    /** @see #makeTimeString(long, boolean) */
    public static String makeTimeStringNanoRounded(long tn) {
        return makeTimeStringNano(tn, true);
    }
    /** @see #makeTimeString(long, boolean) */
    public static String makeTimeStringNano(long tn, boolean round) {
        if (tn<0) return "-"+makeTimeStringNano(-tn, round);
        // units don't matter, but since ms is the usual finest granularity let's use it
        // (previously was just "0" but that was too ambiguous in contexts like "took 0")
        if (tn==0) return "0ms";
        
        long tnm = tn % 1000000;
        long t = tn/1000000;
        String result = "";
        
        long d = t/MILLIS_IN_DAY;  t %= MILLIS_IN_DAY;
        long h = t/MILLIS_IN_HOUR;  t %= MILLIS_IN_HOUR;
        long m = t/MILLIS_IN_MINUTE;  t %= MILLIS_IN_MINUTE;
        long s = t/MILLIS_IN_SECOND;  t %= MILLIS_IN_SECOND;
        long ms = t;
        
        int segments = 0;
        if (d>0) { result += d+"d "; segments++; }
        if (h>0) { result += h+"h "; segments++; }
        if (round && segments>=2) return Strings.removeAllFromEnd(result, " ");
        if (m>0) { result += m+"m "; segments++; }
        if (round && (segments>=2 || d>0)) return Strings.removeAllFromEnd(result, " ");
        if (s>0) {
            if (ms==0 && tnm==0) {
                result += s+"s"; segments++;
                return result;
            }
            if (round && segments>0) {
                result += s+"s"; segments++;
                return result;
            }
            if (round && s>10) {
                result += toDecimal(s, ms/1000.0, 1)+"s"; segments++;
                return result;
            }
            if (round) {
                result += toDecimal(s, ms/1000.0, 2)+"s"; segments++;
                return result;
            }
            result += s+"s ";
        }
        if (round && segments>0)
            return Strings.removeAllFromEnd(result, " ");
        if (ms>0) {
            if (tnm==0) {
                result += ms+"ms"; segments++;
                return result;
            }
            if (round && ms>=100) {
                result += toDecimal(ms, tnm/1000000.0, 1)+"ms"; segments++;
                return result;
            }
            if (round && ms>=10) {
                result += toDecimal(ms, tnm/1000000.0, 2)+"ms"; segments++;
                return result;
            }
            if (round) {
                result += toDecimal(ms, tnm/1000000.0, 3)+"ms"; segments++;
                return result;
            }
            result += ms+"ms ";
        }
        
        long us = tnm/1000;
        long ns = tnm % 1000;

        if (us>0) {
            if (ns==0) {
                result += us+"us"; segments++;
                return result;
            }
            if (round && us>=100) {
                result += toDecimal(us, ns/1000.0, 1)+"us"; segments++;
                return result;
            }
            if (round && us>=10) {
                result += toDecimal(us, ns/1000.0, 2)+"us"; segments++;
                return result;
            }
            if (round) {
                result += toDecimal(us, ns/1000.0, 3)+"us"; segments++;
                return result;
            }
            result += us+"us ";
        }

        if (ns>0) result += ns+"ns";
        return Strings.removeAllFromEnd(result, " ");
    }

    public static Function<Long, String> fromLongToTimeStringExact() { return LONG_TO_TIME_STRING_EXACT; }
    private static final Function<Long, String> LONG_TO_TIME_STRING_EXACT = new FunctionLongToTimeStringExact();
    private static final class FunctionLongToTimeStringExact implements Function<Long, String> {
        @Override @Nullable
        public String apply(@Nullable Long input) {
            if (input == null) return null;
            return Time.makeTimeStringExact(input);
        }
    }

    /** @deprecated since 0.7.0 use {@link #fromLongToTimeStringExact()} */ @Deprecated
    public static Function<Long, String> toTimeString() { return timeString; }
    @Deprecated
    private static Function<Long, String> timeString = new Function<Long, String>() {
            @Override
            @Nullable
            public String apply(@Nullable Long input) {
                if (input == null) return null;
                return Time.makeTimeStringExact(input);
            }
        };
        
    public static Function<Long, String> fromLongToTimeStringRounded() { return LONG_TO_TIME_STRING_ROUNDED; }
    private static final Function<Long, String> LONG_TO_TIME_STRING_ROUNDED = new FunctionLongToTimeStringRounded();
    private static final class FunctionLongToTimeStringRounded implements Function<Long, String> {
        @Override @Nullable
        public String apply(@Nullable Long input) {
            if (input == null) return null;
            return Time.makeTimeStringRounded(input);
        }
    }

    /** @deprecated since 0.7.0 use {@link #fromLongToTimeStringRounded()} */ @Deprecated
    public static Function<Long, String> toTimeStringRounded() { return timeStringRounded; }
    @Deprecated
    private static Function<Long, String> timeStringRounded = new Function<Long, String>() {
        @Override
        @Nullable
        public String apply(@Nullable Long input) {
            if (input == null) return null;
            return Time.makeTimeStringRounded(input);
        }
    };

    public static Function<Duration, String> fromDurationToTimeStringRounded() { return DURATION_TO_TIME_STRING_ROUNDED; }
    private static final Function<Duration, String> DURATION_TO_TIME_STRING_ROUNDED = new FunctionDurationToTimeStringRounded();
    private static final class FunctionDurationToTimeStringRounded implements Function<Duration, String> {
        @Override @Nullable
        public String apply(@Nullable Duration input) {
            if (input == null) return null;
            return Time.makeTimeStringRounded(input);
        }
    }

    private static String toDecimal(long intPart, double fracPart, int decimalPrecision) {
        long powTen = 1;
        for (int i=0; i<decimalPrecision; i++) powTen *= 10;
        long fpr = Math.round(fracPart * powTen);
        if (fpr==powTen) {
            intPart++;
            fpr = 0;
        }
        return intPart + "." + Strings.makePaddedString(""+fpr, decimalPrecision, "0", "");
    }

    /** sleep which propagates Interrupted as unchecked */
    public static void sleep(long millis) {
        try {
            if (millis > 0) Thread.sleep(millis);
        } catch (InterruptedException e) {
            throw Exceptions.propagate(e);
        }
    }
    
    /** as {@link #sleep(long)} */
    public static void sleep(Duration duration) {
        Time.sleep(duration.toMillisecondsRoundingUp());
    }    

    /**
     * Calculates the number of milliseconds past midnight for a given UTC time.
     */
    public static long getTimeOfDayFromUtc(long timeUtc) {
        GregorianCalendar gregorianCalendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        gregorianCalendar.setTimeInMillis(timeUtc);
        int hour = gregorianCalendar.get(Calendar.HOUR_OF_DAY);
        int min = gregorianCalendar.get(Calendar.MINUTE);
        int sec = gregorianCalendar.get(Calendar.SECOND);
        int millis = gregorianCalendar.get(Calendar.MILLISECOND);
        return (((((hour * 60) + min) * 60) + sec) * 1000) + millis;
    }
    
    /**
     * Calculates the number of milliseconds past epoch for a given UTC time.
     */
    public static long getTimeUtc(TimeZone zone, int year, int month, int date, int hourOfDay, int minute, int second, int millis) {
        GregorianCalendar time = new GregorianCalendar(zone);
        time.set(year, month, date, hourOfDay, minute, second);
        time.set(Calendar.MILLISECOND, millis);
        return time.getTimeInMillis();
    }
    
    public static long roundFromMillis(long millis, TimeUnit units) {
        if (units.compareTo(TimeUnit.MILLISECONDS) > 0) {
            double result = ((double)millis) / units.toMillis(1);
            return Math.round(result);
        } else {
            return units.convert(millis, TimeUnit.MILLISECONDS);
        }
    }
    
    public static long roundFromMillis(long millis, long millisPerUnit) {
        double result = ((double)millis) / millisPerUnit;
        return Math.round(result);
    }
    
    /**
     * Calculates how long until maxTime has passed since the given startTime. 
     * However, maxTime==0 is a special case (e.g. could mean wait forever), so the result is guaranteed
     * to be only 0 if maxTime was 0; otherwise -1 will be returned.
     */
    public static long timeRemaining(long startTime, long maxTime) {
        if (maxTime == 0) {
            return 0;
        }
        long result = (startTime+maxTime) - System.currentTimeMillis();
        return (result == 0) ? -1 : result;
    }
    
    /** Convenience for {@link Duration#parse(String)}. */
    public static Duration parseDuration(String timeString) {
        return Duration.parse(timeString);
    }
    
    /** 
     * As {@link #parseElapsedTimeAsDouble(String)}. Consider using {@link #parseDuration(String)} for a more usable return type.
     * 
     * @throws NumberFormatException if cannot be parsed (or if null)
     */
    public static long parseElapsedTime(String timeString) {
        return (long) parseElapsedTimeAsDouble(timeString);
    }
    /** @deprecated since 0.7.0 see {@link #parseElapsedTime(String)} */ @Deprecated
    public static long parseTimeString(String timeString) {
        return (long) parseElapsedTime(timeString);
    }
    /** @deprecated since 0.7.0 see {@link #parseElapsedTimeAsDouble(String)} */ @Deprecated
    public static double parseTimeStringAsDouble(String timeString) {
        return parseElapsedTimeAsDouble(timeString);
    }
    
    /** 
     * Parses a string eg '5s' or '20m 22.123ms', returning the number of milliseconds it represents; 
     * -1 on blank or never or off or false.
     * Assumes unit is millisections if no unit is specified.
     * 
     * @throws NumberFormatException if cannot be parsed (or if null)
     */
    public static double parseElapsedTimeAsDouble(String timeString) {
        if (timeString==null)
            throw new NumberFormatException("GeneralHelper.parseTimeString cannot parse a null string");
        try {
            double d = Double.parseDouble(timeString);
            return d;
        } catch (NumberFormatException e) {
            //look for a type marker
            timeString = timeString.trim();
            String s = Strings.getLastWord(timeString).toLowerCase();
            timeString = timeString.substring(0, timeString.length()-s.length()).trim();
            int i=0;
            while (s.length()>i) {
                char c = s.charAt(i);
                if (c=='.' || Character.isDigit(c)) i++;
                else break;
            }
            String num = s.substring(0, i);
            if (i==0) {
                num = Strings.getLastWord(timeString).toLowerCase();
                timeString = timeString.substring(0, timeString.length()-num.length()).trim();
            } else {
                s = s.substring(i);
            }
            long multiplier = 0;
            if (num.length()==0) {
                //must be never or something
                if (s.equalsIgnoreCase("never") || s.equalsIgnoreCase("off") || s.equalsIgnoreCase("false"))
                    return -1;
                throw new NumberFormatException("unrecognised word  '"+s+"' in time string");
            }
            if (s.equalsIgnoreCase("ms") || s.equalsIgnoreCase("milli") || s.equalsIgnoreCase("millis")
                    || s.equalsIgnoreCase("millisec") || s.equalsIgnoreCase("millisecs")
                    || s.equalsIgnoreCase("millisecond") || s.equalsIgnoreCase("milliseconds"))
                multiplier = 1;
            else if (s.equalsIgnoreCase("s") || s.equalsIgnoreCase("sec") || s.equalsIgnoreCase("secs")
                    || s.equalsIgnoreCase("second") || s.equalsIgnoreCase("seconds"))
                multiplier = 1000;
            else if (s.equalsIgnoreCase("m") || s.equalsIgnoreCase("min") || s.equalsIgnoreCase("mins")
                    || s.equalsIgnoreCase("minute") || s.equalsIgnoreCase("minutes"))
                multiplier = 60*1000;
            else if (s.equalsIgnoreCase("h") || s.equalsIgnoreCase("hr") || s.equalsIgnoreCase("hrs")
                    || s.equalsIgnoreCase("hour") || s.equalsIgnoreCase("hours"))
                multiplier = 60*60*1000;
            else if (s.equalsIgnoreCase("d") || s.equalsIgnoreCase("day") || s.equalsIgnoreCase("days"))
                multiplier = 24*60*60*1000;
            else
                throw new NumberFormatException("unknown unit '"+s+"' in time string");
            double d = Double.parseDouble(num);
            double dd = 0;
            if (timeString.length()>0) {
                dd = parseElapsedTimeAsDouble(timeString);
                if (dd==-1) {
                    throw new NumberFormatException("cannot combine '"+timeString+"' with '"+num+" "+s+"'");
                }
            }
            return d*multiplier + dd;
        }
    }

    public static Calendar newCalendarFromMillisSinceEpochUtc(long timestamp) {
        GregorianCalendar cal = new GregorianCalendar();
        cal.setTimeInMillis(timestamp);
        return cal;
    }

    public static Calendar newCalendarFromDate(Date date) {
        return newCalendarFromMillisSinceEpochUtc(date.getTime());
    }
    
    /** As {@link #parseCalendar(String)} but returning a {@link Date},
     * (i.e. a record where the time zone has been applied and forgotten). */
    public static Date parseDate(String input) {
        if (input==null) return null;
        return parseCalendarMaybe(input).get().getTime();
    }

    /** Parses dates from string, accepting many formats including ISO-8601 and http://yaml.org/type/timestamp.html, 
     * e.g. 2015-06-15 16:00:00 +0000.
     * <p>
     * Millis since epoch (1970) is also supported to represent the epoch (0) or dates in this millenium,
     * but to prevent ambiguity of e.g. "20150615", any other dates prior to the year 2001 are not accepted.
     * (However if a type Long is supplied, e.g. from a YAML parse, it will always be treated as millis since epoch.) 
     * <p>
     * Other formats including locale-specific variants, e.g. recognising month names,
     * are supported but this may vary from platform to platform and may change between versions. */
    public static Calendar parseCalendar(String input) {
        if (input==null) return null;
        return parseCalendarMaybe(input).get();
    }
    
    /** as {@link #parseCalendar(String)} but returning a {@link Maybe} rather than throwing or returning null */
    public static Maybe<Calendar> parseCalendarMaybe(String input) {
        if (input==null) return Maybe.absent("value is null");
        input = input.trim();
        Maybe<Calendar> result;

        result = parseCalendarUtc(input);
        if (result.isPresent()) return result;

        result = parseCalendarSimpleFlexibleFormatParser(input);
        if (result.isPresent()) return result;
        // return the error from this method
        Maybe<Calendar> returnResult = result;

        result = parseCalendarFormat(input, new SimpleDateFormat(DATE_FORMAT_OF_DATE_TOSTRING, Locale.ROOT));
        if (result.isPresent()) return result;
        result = parseCalendarDefaultParse(input);
        if (result.isPresent()) return result;

        return returnResult;
    }

    @SuppressWarnings("deprecation")
    private static Maybe<Calendar> parseCalendarDefaultParse(String input) {
        try {
            long ms = Date.parse(input);
            if (ms>=new Date(1999, 12, 25).getTime() && ms <= new Date(2200, 1, 2).getTime()) {
                // accept default date parse for this century and next
                GregorianCalendar c = new GregorianCalendar();
                c.setTimeInMillis(ms);
                return Maybe.of((Calendar)c);
            }
        } catch (Exception e) {
            Exceptions.propagateIfFatal(e);
        }
        return Maybe.absent();
    }

    private static Maybe<Calendar> parseCalendarUtc(String input) {
        input = input.trim();
        if (input.matches("\\d+")) {
            if ("0".equals(input)) {
                // accept 0 as epoch UTC
                return Maybe.of(newCalendarFromMillisSinceEpochUtc(0));
            }
            Maybe<Calendar> result = Maybe.of(newCalendarFromMillisSinceEpochUtc(Long.parseLong(input)));
            if (result.isPresent()) {
                int year = result.get().get(Calendar.YEAR);
                if (year >= 2000 && year < 2200) {
                    // only applicable for dates in this century
                    return result;
                } else {
                    return Maybe.absent("long is probably not millis since epoch UTC; millis as string is not in acceptable range");
                }
            }
        }
        return Maybe.absent("not long millis since epoch UTC");
    }

    private final static String DIGIT = "\\d";
    private final static String LETTER = "\\p{L}";
    private final static String COMMON_SEPARATORS = "-\\.";
    private final static String TIME_SEPARATOR = COMMON_SEPARATORS+":";
    private final static String DATE_SEPARATOR = COMMON_SEPARATORS+"/ ";
    private final static String DATE_TIME_ANY_ORDER_GROUP_SEPARATOR = COMMON_SEPARATORS+":/ ";

    private final static String DATE_ONLY_WITH_INNER_SEPARATORS = 
            namedGroup("year", DIGIT+DIGIT+DIGIT+DIGIT) +
            anyChar(DATE_SEPARATOR) +
            namedGroup("month", options(optionally(DIGIT)+DIGIT, anyChar(LETTER)+"+")) +
            anyChar(DATE_SEPARATOR) +
            namedGroup("day", optionally(DIGIT)+DIGIT);
    private final static String DATE_WORDS_2 = 
            namedGroup("month", anyChar(LETTER)+"+") +
            anyChar(DATE_SEPARATOR) +
            namedGroup("day", optionally(DIGIT)+DIGIT) +
            ",?"+anyChar(DATE_SEPARATOR)+"+" +
            namedGroup("year", DIGIT+DIGIT+DIGIT+DIGIT);
    // we could parse NN-NN-NNNN as DD-MM-YYYY always, but could be confusing for MM-DD-YYYY oriented people, so require month named
    private final static String DATE_WORDS_3 = 
            namedGroup("day", optionally(DIGIT)+DIGIT) +
            anyChar(DATE_SEPARATOR) +
            namedGroup("month", anyChar(LETTER)+"+") +
            ",?"+anyChar(DATE_SEPARATOR)+"+" +
            namedGroup("year", DIGIT+DIGIT+DIGIT+DIGIT);

    private final static String DATE_ONLY_NO_SEPARATORS = 
            namedGroup("year", DIGIT+DIGIT+DIGIT+DIGIT) +
            namedGroup("month", DIGIT+DIGIT) +
            namedGroup("day", DIGIT+DIGIT);

    private final static String MERIDIAN = anyChar("aApP")+optionally(anyChar("mM"));
    private final static String TIME_ONLY_WITH_INNER_SEPARATORS = 
            namedGroup("hours", optionally(DIGIT)+DIGIT)+
            optionally(
                anyChar(TIME_SEPARATOR)+
                namedGroup("mins", DIGIT+DIGIT)+
                optionally(
                    anyChar(TIME_SEPARATOR)+
                    namedGroup("secs", DIGIT+DIGIT+optionally( optionally("\\.")+DIGIT+"+"))))+
            optionally(" *" + namedGroup("meridian", notMatching(LETTER+LETTER+LETTER)+MERIDIAN));
    private final static String TIME_ONLY_NO_SEPARATORS = 
            namedGroup("hours", DIGIT+DIGIT)+
            namedGroup("mins", DIGIT+DIGIT)+
            optionally(
                namedGroup("secs", DIGIT+DIGIT+optionally( optionally("\\.")+DIGIT+"+")))+
                namedGroup("meridian", "");

    private final static String TZ_CODE = 
            namedGroup("tzCode",
                notMatching(MERIDIAN+options("$", anyChar("^"+LETTER))) + // not AM or PM
                anyChar(LETTER)+"+"+anyChar(LETTER+DIGIT+"\\/\\-\\' _")+"*");
    private final static String TIME_ZONE_SIGNED_OFFSET = 
            namedGroup("tz", 
                options(
                    namedGroup("tzOffset", options("\\+", "-")+
                        DIGIT+optionally(DIGIT)+optionally(optionally(":")+DIGIT+DIGIT)), 
                    optionally("\\+")+TZ_CODE));
    private final static String TIME_ZONE_OPTIONALLY_SIGNED_OFFSET = 
            namedGroup("tz", 
                options(
                    namedGroup("tzOffset", options("\\+", "-", " ")+
                        options("0"+DIGIT, "10", "11", "12")+optionally(optionally(":")+DIGIT+DIGIT)), 
                    TZ_CODE));

    private static String getDateTimeSeparatorPattern(String extraChars) {
        return 
            options(
                " +"+optionally(anyChar(DATE_TIME_ANY_ORDER_GROUP_SEPARATOR+extraChars+",")),
                anyChar(DATE_TIME_ANY_ORDER_GROUP_SEPARATOR+extraChars+",")) +
            anyChar(DATE_TIME_ANY_ORDER_GROUP_SEPARATOR+extraChars)+"*";
    }
    
    @SuppressWarnings("deprecation")
    // we have written our own parsing because the alternatives were either too specific or too general
    // java and apache and even joda-time are too specific, and would require explosion of patterns to be flexible;
    // Natty - https://github.com/joestelmach/natty - is very cool, but it drags in ANTLR,
    // it doesn't support dashes between date and time, and 
    // it encourages relative time which would be awesome but only if we resolved it on read
    // (however there is natty code to parseDateNatty in the git history if we did want to use it)
    private static Maybe<Calendar> parseCalendarSimpleFlexibleFormatParser(String input) {
        input = input.trim();

        String[] DATE_PATTERNS = new String[] {
            DATE_ONLY_WITH_INNER_SEPARATORS,
            DATE_ONLY_NO_SEPARATORS,
            DATE_WORDS_2,
            DATE_WORDS_3,            
        };
        String[] TIME_PATTERNS = new String[] {
            TIME_ONLY_WITH_INNER_SEPARATORS,
            TIME_ONLY_NO_SEPARATORS            
        };
        String[] TZ_PATTERNS = new String[] {
            // space then time zone with sign (+-) or code is preferred
            optionally(getDateTimeSeparatorPattern("")) + " " + TIME_ZONE_SIGNED_OFFSET,
            // then no TZ - but declare the named groups
            namedGroup("tz", namedGroup("tzOffset", "")+namedGroup("tzCode", "")),
            // then any separator then offset with sign
            getDateTimeSeparatorPattern("") + TIME_ZONE_SIGNED_OFFSET,
            
            // try parsing with enforced separators before TZ first 
            // (so e.g. in the case of DATE-0100, the -0100 is the time, not the timezone)
            // then relax below (e.g. in the case of DATE-TIME+0100)
            
            // finally match DATE-TIME-1000 as time zone -1000
            // or DATE-TIME 1000 as TZ +1000 in case a + was supplied but converted to ' ' by web
            // (but be stricter about the format, two or four digits required, and hours <= 12 so as not to confuse with a year)
            optionally(getDateTimeSeparatorPattern("")) + TIME_ZONE_OPTIONALLY_SIGNED_OFFSET
        };
        
        List<String> basePatterns = MutableList.of();
        
        // patterns with date first
        String[] DATE_PATTERNS_UNCLOSED = new String[] {
            // separator before time *required* if date had separators
            DATE_ONLY_WITH_INNER_SEPARATORS + "("+getDateTimeSeparatorPattern("Tt"),
            // separator before time optional if date did not have separators
            DATE_ONLY_NO_SEPARATORS + "("+optionally(getDateTimeSeparatorPattern("Tt")),
            // separator before time required if date has words
            DATE_WORDS_2 + "("+getDateTimeSeparatorPattern("Tt"),
            DATE_WORDS_3 + "("+getDateTimeSeparatorPattern("Tt"),
        };
        for (String tzP: TZ_PATTERNS)
            for (String dateP: DATE_PATTERNS_UNCLOSED)
                for (String timeP: TIME_PATTERNS)
                    basePatterns.add(dateP + timeP+")?" + tzP);
        
        // also allow time first, with TZ after, then before
        for (String tzP: TZ_PATTERNS)
            for (String dateP: DATE_PATTERNS)
                for (String timeP: TIME_PATTERNS)
                    basePatterns.add(timeP + getDateTimeSeparatorPattern("") + dateP + tzP);
        // also allow time first, with TZ after, then before
        for (String tzP: TZ_PATTERNS)
            for (String dateP: DATE_PATTERNS)
                for (String timeP: TIME_PATTERNS)
                    basePatterns.add(timeP + tzP + getDateTimeSeparatorPattern("") + dateP);

        Maybe<Matcher> mm = Maybe.absent();
        for (String p: basePatterns) {
            mm = match(p, input);
            if (mm.isPresent()) break;
        }
        if (mm.isPresent()) {
            Matcher m = mm.get();
            Calendar result;

            String tz = m.group("tz");
            
            int year = Integer.parseInt(m.group("year"));
            int day = Integer.parseInt(m.group("day"));
            
            String monthS = m.group("month");
            int month;
            if (monthS.matches(DIGIT+"+")) {
                month = Integer.parseInt(monthS)-1;
            } else {
                try {
                    month = new SimpleDateFormat("yyyy-MMM-dd", Locale.ROOT).parse("2015-"+monthS+"-15").getMonth();
                } catch (ParseException e) {
                    return Maybe.absent("Unknown date format '"+input+"': invalid month '"+monthS+"'; try http://yaml.org/type/timestamp.html format e.g. 2015-06-15 16:00:00 +0000");
                }
            }
            
            if (Strings.isNonBlank(tz)) {
                TimeZone tzz = null;
                String tzCode = m.group("tzCode");
                if (Strings.isNonBlank(tzCode)) {
                    tz = tzCode;
                }
                if (tz.matches(DIGIT+"+")) {
                    // stick a plus in front in case it was submitted by a web form and turned into a space
                    tz = "+"+tz;
                } else {
                    tzz = getTimeZone(tz);
                }
                if (tzz==null) {
                    Maybe<Matcher> tmm = match(" ?(?<tzH>(\\+|\\-||)"+DIGIT+optionally(DIGIT)+")"+optionally(optionally(":")+namedGroup("tzM", DIGIT+DIGIT)), tz);
                    if (tmm.isAbsent()) {
                        return Maybe.absent("Unknown date format '"+input+"': invalid timezone '"+tz+"'; try http://yaml.org/type/timestamp.html format e.g. 2015-06-15 16:00:00 +0000");
                    }
                    Matcher tm = tmm.get();
                    String tzM = tm.group("tzM");
                    int offset = (60*Integer.parseInt(tm.group("tzH")) + Integer.parseInt("0"+(tzM!=null ? tzM : "")))*60;
                    tzz = new SimpleTimeZone(offset*1000, tz);
                }
                tz = getTimeZoneOffsetString(tzz, year, month, day);
                result = new GregorianCalendar(tzz);
            } else {
                result = new GregorianCalendar();
            }
            result.clear();
            
            result.set(Calendar.YEAR, year);
            result.set(Calendar.MONTH, month);
            result.set(Calendar.DAY_OF_MONTH, day);
            if (m.group("hours")!=null) {
                int hours = Integer.parseInt(m.group("hours"));
                String meridian = m.group("meridian");
                if (Strings.isNonBlank(meridian) && meridian.toLowerCase().startsWith("p")) {
                    if (hours>12) {
                        return Maybe.absent("Unknown date format '"+input+"': can't be "+hours+" PM; try http://yaml.org/type/timestamp.html format e.g. 2015-06-15 16:00:00 +0000");
                    }
                    hours += 12;
                }
                result.set(Calendar.HOUR_OF_DAY, hours);
                String minsS = m.group("mins");
                if (Strings.isNonBlank(minsS)) {
                    result.set(Calendar.MINUTE, Integer.parseInt(minsS));
                }
                String secsS = m.group("secs");
                if (Strings.isBlank(secsS)) {
                    // leave at zero
                } else if (secsS.matches(DIGIT+DIGIT+"?")) {
                    result.set(Calendar.SECOND, Integer.parseInt(secsS));
                } else {
                    double s = Double.parseDouble(secsS);
                    if (secsS.indexOf('.')>=0) {
                        // accept
                    } else if (secsS.length()==5) {
                        // allow ssSSS with no punctuation
                        s = s/=1000;
                    } else {
                        return Maybe.absent("Unknown date format '"+input+"': invalid seconds '"+secsS+"'; try http://yaml.org/type/timestamp.html format e.g. 2015-06-15 16:00:00 +0000");
                    }
                    result.set(Calendar.SECOND, (int)s);
                    result.set(Calendar.MILLISECOND, (int)((s*1000) % 1000));
                }
            }
            
            return Maybe.of(result);
        }
        return Maybe.absent("Unknown date format '"+input+"'; try http://yaml.org/type/timestamp.html format e.g. 2015-06-15 16:00:00 +0000");
    }
    
    public static TimeZone getTimeZone(String code) {
        if (code.indexOf('/')==-1) {
            if ("Z".equals(code)) return TIME_ZONE_UTC;
            if ("UTC".equals(code)) return TIME_ZONE_UTC;
            if ("GMT".equals(code)) return TIME_ZONE_UTC;
            
            // get the time zone -- most short codes aren't accepted, so accept (and prefer) certain common codes
            if ("EST".equals(code)) return getTimeZone("America/New_York");
            if ("EDT".equals(code)) return getTimeZone("America/New_York");
            if ("PST".equals(code)) return getTimeZone("America/Los_Angeles");
            if ("PDT".equals(code)) return getTimeZone("America/Los_Angeles");
            if ("CST".equals(code)) return getTimeZone("America/Chicago");
            if ("CDT".equals(code)) return getTimeZone("America/Chicago");
            if ("MST".equals(code)) return getTimeZone("America/Denver");
            if ("MDT".equals(code)) return getTimeZone("America/Denver");

            if ("BST".equals(code)) return getTimeZone("Europe/London");  // otherwise BST is Bangladesh!
            if ("CEST".equals(code)) return getTimeZone("Europe/Paris");
            // IST falls through to below, where it is treated as India (not Irish); IDT not recognised
        }
        
        TimeZone tz = TimeZone.getTimeZone(code);
        if (tz!=null && !tz.equals(TimeZone.getTimeZone("GMT"))) {
            // recognized
            return tz;
        }
        // possibly unrecognized -- GMT returned if not known, bad TimeZone API!
        String timeZones[] = TimeZone.getAvailableIDs();
        for (String tzs: timeZones) {
            if (tzs.equals(code)) return tz;
        }
        // definitely unrecognized
        return null;
    }
    
    /** convert a TimeZone e.g. Europe/London to an offset string as at the given day, e.g. +0100 or +0000 depending daylight savings,
     * absent with nice error if zone unknown */
    public static Maybe<String> getTimeZoneOffsetString(String tz, int year, int month, int day) {
        TimeZone tzz = getTimeZone(tz);
        if (tzz==null) return Maybe.absent("Unknown time zone code: "+tz);
        return Maybe.of(getTimeZoneOffsetString(tzz, year, month, day));
    }
    
    /** as {@link #getTimeZoneOffsetString(String, int, int, int)} where the {@link TimeZone} is already instantiated */
    @SuppressWarnings("deprecation")
    public static String getTimeZoneOffsetString(TimeZone tz, int year, int month, int day) {
        int tzMins = tz.getOffset(new Date(year, month, day).getTime())/60/1000;
        String tzStr = (tzMins<0 ? "-" : "+") + Strings.makePaddedString(""+(Math.abs(tzMins)/60), 2, "0", "")+Strings.makePaddedString(""+(Math.abs(tzMins)%60), 2, "0", "");
        return tzStr;
    }

    private static String namedGroup(String name, String pattern) {
        return "(?<"+name+">"+pattern+")";
    }
    private static String anyChar(String charSet) {
        return "["+charSet+"]";
    }
    private static String optionally(String pattern) {
        return "("+pattern+")?";
    }
    private static String options(String ...patterns) {
        return "("+Strings.join(patterns,"|")+")";
    }
    private static String notMatching(String pattern) {
        return "(?!"+pattern+")";
    }
    
    private static Maybe<Matcher> match(String pattern, String input) {
        Matcher m = Pattern.compile("^"+pattern+"$").matcher(input);
        if (m.find()) return Maybe.of(m);
        return Maybe.absent();
    }

    /**
     * Parses the given date, accepting either a UTC timestamp (i.e. a long), or a formatted date.
     * <p>
     * If no time zone supplied, this defaults to the TZ configured at the brooklyn server.
     * 
     * @deprecated since 0.7.0 use {@link #parseCalendar(String)} for general or {@link #parseCalendarFormat(String, DateFormat)} for a format,
     * plus {@link #parseCalendarUtc(String)} if you want to accept UTC
     */
    public static Date parseDateString(String dateString, DateFormat format) {
        Maybe<Calendar> r = parseCalendarFormat(dateString, format);
        if (r.isPresent()) return r.get().getTime();
        
        r = parseCalendarUtc(dateString);
        if (r.isPresent()) return r.get().getTime();

        throw new IllegalArgumentException("Date " + dateString + " cannot be parsed as UTC millis or using format " + format);
    }
    public static Maybe<Calendar> parseCalendarFormat(String dateString, String format) {
        return parseCalendarFormat(dateString, new SimpleDateFormat(format, Locale.ROOT));
    }
    public static Maybe<Calendar> parseCalendarFormat(String dateString, DateFormat format) {
        if (dateString == null) { 
            throw new NumberFormatException("GeneralHelper.parseDateString cannot parse a null string");
        }
        Preconditions.checkNotNull(format, "date format");
        dateString = dateString.trim();
        
        ParsePosition p = new ParsePosition(0);
        Date result = format.parse(dateString, p);
        if (result!=null) {
            // accept results even if the entire thing wasn't parsed, as enough was to match the requested format
            return Maybe.of(newCalendarFromDate(result));
        }
        if (log.isTraceEnabled()) log.trace("Could not parse date "+dateString+" using format "+format+": "+p);
        return Maybe.absent();
    }

    /** removes milliseconds from the date object; needed if serializing to ISO-8601 format 
     * and want to serialize back and get the same data */
    public static Date dropMilliseconds(Date date) {
        return date==null ? null : date.getTime()%1000!=0 ? new Date(date.getTime() - (date.getTime()%1000)) : date;
    }

    /** returns the duration elapsed since the given timestamp (UTC) */
    public static Duration elapsedSince(long timestamp) {
        return Duration.millis(System.currentTimeMillis() - timestamp);
    }
    
    /** true iff it has been longer than the given duration since the given timestamp */
    public static boolean hasElapsedSince(long timestamp, Duration duration) {
        return elapsedSince(timestamp).compareTo(duration) > 0;
    }

    /** more readable and shorter convenience for System.currentTimeMillis() */
    public static long now() {
        return System.currentTimeMillis();
    }
    
}
