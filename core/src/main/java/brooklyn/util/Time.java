/*
 * Copyright (c) 2009-2011 Cloudsoft Corporation Ltd. All rights reserved.
 * Supplied under license http://www.cloudsoftcorp.com/license/montereyDeveloperEdition
 * or such subsequent license agreed between Cloudsoft Corporation Ltd and the licensee.
 */
package brooklyn.util;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import brooklyn.util.text.Strings;

import com.google.common.base.Throwables;

public class Time {

	public static String DATE_FORMAT_PREFERRED = "yyyy-MM-dd HH:mm:ss.SSS";

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

	/** given an elapsed time, makes it readable, eg 44d 6h, or 8s 923ms */
    public static String makeTimeString(long t, TimeUnit unit) {
        long nanos = unit.toNanos(t);
        return makeTimeStringNano(nanos);
    }

	/** given an elapsed time in ms, makes it readable, eg 44d 6h, or 8s 923ms */
	public static String makeTimeString(long t) {
		if (t==0) return "0ms";
		if (t<0) return "-"+makeTimeString(-t);
		long d = t/MILLIS_IN_DAY, dr = t%MILLIS_IN_DAY;
		long h = dr/MILLIS_IN_HOUR, hr = dr%MILLIS_IN_HOUR;
		long m = hr/MILLIS_IN_MINUTE, mr = hr%MILLIS_IN_MINUTE;
		long s = mr/MILLIS_IN_SECOND, sr = mr%MILLIS_IN_SECOND;
		long ms = sr;
		String result = "";                          //@maydo, doesn't do rounding
		
		if (d>0) result += d+"d ";
		if (h>0 || (d>0 && dr>0)) result += h+"h ";
		if (d==0 && (m>0 || (h>0 && mr>0))) result += m+"m ";
		if (d>0 || h>0) {
			//nothing more
		} else if (m>0) {
			//seconds only
			if (mr>0) result += s+"s";
		} else {
			//seconds, and millis maybe
			if (s>=30) result += s+"s";
			else if (s>=10) {
				if (sr>0) {
					int sRound = Math.round(s*10+sr/100);
					result += sRound/10 +"."+(s%10)+"s";
				}
				else result += s+"s";
			} else {
				//seconds and millis
				if (s>=1) result += s+"s ";
				if (ms>0) result += ms+"ms";
				else if (s==0) result += "0ms";
			}
		}
		if (result.endsWith(" ")) result=result.substring(0, result.length()-1);
		return result;
	}

	public static String makeTimeStringNano(long tn) {
		long tnm = tn % 1000000;
		long t = tn/1000000;
		String result = "";                          //@maydo, doesn't do rounding; oh now i think it does, but check
		if (t>=100 || (t>0 && tnm==0)) {
			long d = (t/1000/60/60/24);
			long h = ( (t % (1000*60*60*24))/1000/60/60 );
			long m = ( (t % (1000*60*60))/1000/60 );
			long s = ( (t % (1000*60))/1000 );
			long ms = ( (t % (1000))/1 );
			if (d>0) result += d+"d ";
			if (d>0 || h>0) result += h+"h ";
			if (d==0 && (h>0 || m>0)) result += m+"m ";
			if (d==0 && h==0 && (m>0 || s>0)) {
				if (m>0 || s<10) result += s+"s ";
				else result += toDecimal(s, ms/1000.d, 1);
			}
			if (d==0 && h==0 && m==0 && s<10) result += ms+"ms ";
		} else {
			if (t>=10) result += toDecimal(t, tnm/1000000.0d, 1);
			else if (t>0) result += toDecimal(t, tnm/1000000.0d, 2);
			else if (tnm==0) result = "0";
//			else if (tnm%1000==0) result += t+"."+StringUtils.makePaddedString(""+Math.round(tnm/1000.0d),3,"0","");  //normally only microsec precision
//			//else result += t+"."+makePaddedString(""+(tnm),6,"0","");  //though maybe more somewhere (not that i've seen from nanoTime, but derived maybe)
			
			if (result.length()>0) result+="ms";
			else if (tnm>100*1000) result = Math.round(tnm/1000.0d)+"us";
			else if (tnm>10*1000) result = toDecimal(tnm/1000, (tnm%1000)/1000.0d, 1) +"us";
			else if (tnm>1000) result = toDecimal(tnm/1000, (tnm%1000)/1000.0d, 2) +"us";
			else result = tnm+"ns";
		}
		if (result.endsWith(" ")) result=result.substring(0, result.length()-1);
		return result;
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

	public static String makeTimeStringNanoLong(long tn) {
		long tnm = tn % 1000000;
		long t = tn/1000000;
		String result = "";                          //@maydo, doesn't do rounding
		if (t>=1000) {
			long d = (t/1000/60/60/24);
			long h = ( (t % (1000*60*60*24))/1000/60/60 );
			long m = ( (t % (1000*60*60))/1000/60 );
			long s = ( (t % (1000*60))/1000 );
			if (d>0) result += d+"d ";
			if (h>0) result += h+"h ";
			if (m>0) result += m+"m ";
			if (s>0) result += s+"s ";
		}
		result += (t%1000)+"."+Strings.makePaddedString(""+(tnm),6,"0","");
		result += "ms ";
		if (result.endsWith(" ")) result=result.substring(0, result.length()-1);
		return result;
	}

	/** sleep which propagates Interrupted as unchecked */
	public static void sleep(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			throw Throwables.propagate(e);
		}
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
	 * -1 on blank or never or off or false;
	 * number of millis if no units specified;
	 * otherwise throws Parse exception (including on null */
	public static long parseTimeString(String timeString) {
		return (long) parseTimeStringAsDouble(timeString);
	}

	/** parses a string eg '5s' or '20m 22.123ms', returning the number of milliseconds it represents; -1 on blank or never or off or false;
	 * number of millis if no units specified;
	 * otherwise throws Parse exception (including on null */
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
	
}
