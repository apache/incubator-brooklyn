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
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.text.Strings;

import com.google.common.base.Function;
import com.google.common.base.Stopwatch;

public class Time {

	public static final String DATE_FORMAT_PREFERRED = "yyyy-MM-dd HH:mm:ss.SSS";
	public static final String DATE_FORMAT_STAMP = "yyyyMMdd-HHmmssSSS";

	public static final long MILLIS_IN_SECOND = 1000;
	public static final long MILLIS_IN_MINUTE = 60*MILLIS_IN_SECOND;
	public static final long MILLIS_IN_HOUR = 60*MILLIS_IN_MINUTE;
	public static final long MILLIS_IN_DAY = 24*MILLIS_IN_HOUR;
	public static final long MILLIS_IN_YEAR = 365*MILLIS_IN_DAY;
	
    /** returns the current time in YYYY-MM-DD HH:MM:SS.mss format */
    public static String makeDateString() {
        return makeDateString(System.currentTimeMillis());
    }

	/** returns the time in YYYY-MM-DD HH:MM:SS.mss format, given a long (e.g. returned by System.currentTimeMillis) */
	public static String makeDateString(long date) {
		return new SimpleDateFormat(DATE_FORMAT_PREFERRED).format(new Date(date));
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

    /** returns the current time in YYYYMMDD-HHMMSSmss format */
    public static String makeDateStampString() {
        return makeDateStampString(System.currentTimeMillis());
    }

    /** returns the time in YYYY-MM-DD HH:MM:SS.mss format, given a long (e.g. returned by System.currentTimeMillis) */
    public static String makeDateStampString(long date) {
        return new SimpleDateFormat(DATE_FORMAT_STAMP).format(new Date(date));
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
	    if (tn==0) return "0";
	    
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

    public static Function<Long, String> toTimeString() { return timeString; }
    private static Function<Long, String> timeString = new Function<Long, String>() {
            @Override
            @Nullable
            public String apply(@Nullable Long input) {
                if (input == null) return null;
                return Time.makeTimeStringExact(input);
            }
        };

    public static Function<Long, String> toTimeStringRounded() { return timeStringRounded; }
    private static Function<Long, String> timeStringRounded = new Function<Long, String>() {
            @Override
            @Nullable
            public String apply(@Nullable Long input) {
                if (input == null) return null;
                return Time.makeTimeStringRounded(input);
            }
        };

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
			Thread.sleep(millis);
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
    
	/** parses a string eg '5s' or '20m 22.123ms', returning the number of milliseconds it represents (rounded);
	 * -1 on blank or "never" or "off" or "false";
	 * number of millis if no units specified.
	 * 
	 * @throws NumberFormatException if cannot be parsed (or if null)
	 */
	public static long parseTimeString(String timeString) {
		return (long) parseTimeStringAsDouble(timeString);
	}

	/** parses a string eg '5s' or '20m 22.123ms', returning the number of milliseconds it represents; -1 on blank or never or off or false;
	 * number of millis if no units specified.
	 * 
     * @throws NumberFormatException if cannot be parsed (or if null)
	 */
	public static double parseTimeStringAsDouble(String timeString) {
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
				dd = parseTimeStringAsDouble(timeString);
				if (dd==-1) {
					throw new NumberFormatException("cannot combine '"+timeString+"' with '"+num+" "+s+"'");
				}
			}
			return d*multiplier + dd;
		}
	}

	/**
	 * Parses the given date, accepting either a UTC timestamp (i.e. a long), or a formatted date.
	 * @param dateString
	 * @param format
	 * @return
	 */
    public static Date parseDateString(String dateString, DateFormat format) {
        if (dateString == null) 
            throw new NumberFormatException("GeneralHelper.parseDateString cannot parse a null string");

        try {
            return format.parse(dateString);
        } catch (ParseException e) {
            // continue trying
        }
        
        try {
            // fix the usual ' ' => '+' nonsense when passing dates as query params
            // note this is not URL unescaping, but converting to the date format that wants "+" in the middle and not spaces
            String transformedDateString = dateString.replace(" ", "+");
            return format.parse(transformedDateString);
        } catch (ParseException e) {
            // continue trying
        }
        
        try {
            return new Date(Long.parseLong(dateString.trim())); // try UTC millis number
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Date " + dateString + " cannot be parsed as UTC millis or using format " + format);
        }
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
