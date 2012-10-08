/*
 * Copyright (c) 2009-2011 Cloudsoft Corporation Ltd. All rights reserved.
 * Supplied under license http://www.cloudsoftcorp.com/license/montereyDeveloperEdition
 * or such subsequent license agreed between Cloudsoft Corporation Ltd and the licensee.
 */
package brooklyn.util.text;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Map;

import brooklyn.util.Time;

public class Strings {

    /**
     * Checks if the given string is null or is an empty string.
     * Useful for pre-String.isEmpty.  And useful for StringBuilder etc.
     *
     * @param s the String to check
     * @return  true if empty or null, false otherwise.
     */
    public static boolean isEmpty(CharSequence s) {
        return s == null || s.length()==0;
    }

	/** removes the first suffix in the list which is present at the end of string
	 * and returns that string; ignores subsequent suffixes if a matching one is found;
	 * returns the original string if no suffixes are at the end
	 */
	public static String removeFromEnd(String string, String ...suffixes) {
	    if (isEmpty(string)) return string;
		for (String suffix : suffixes)
			if (string.endsWith(suffix)) return string.substring(0, string.length() - suffix.length());
		return string;
	}

    /** as removeFromEnd, but repeats until all such suffixes are gone */
    public static String removeAllFromEnd(String string, String ...suffixes) {
        boolean anotherLoopNeeded = true;
        while (anotherLoopNeeded) {
            if (isEmpty(string)) return string;
            anotherLoopNeeded = false;
            for (String suffix : suffixes)
                if (string.endsWith(suffix)) {
                    string = string.substring(0, string.length() - suffix.length());
                    anotherLoopNeeded = true;
                    break;
                }
        }
        return string;
    }
    
    /** removes the first prefix in the list which is present at the start of string
     * and returns that string; ignores subsequent prefixes if a matching one is found;
     * returns the original string if no prefixes match
     */
    public static String removeFromStart(String string, String ...prefixes) {
        if (isEmpty(string)) return string;
        for (String prefix : prefixes)
            if (string.startsWith(prefix)) return string.substring(prefix.length());
        return string;
    }

    /** as removeFromStart, but repeats until all such suffixes are gone */
    public static String removeAllFromStart(String string, String ...prefixes) {
        boolean anotherLoopNeeded = true;
        while (anotherLoopNeeded) {
            if (isEmpty(string)) return string;
            anotherLoopNeeded = false;
            for (String prefix : prefixes)
                if (string.startsWith(prefix)) {
                    string = string.substring(prefix.length());
                    anotherLoopNeeded = true;
                    break;
                }
        }
        return string;
    }

	/** given a list containing e.g. "a", "b", with separator "," produces "a,b" */
	public static String join(Iterable<? extends Object> list, String seperator) {
		boolean app = false;
		StringBuilder out = new StringBuilder();
		for (Object s: list) {
			if (app) out.append(seperator);
			out.append(s);
			app = true;
		}
		return out.toString();
	}
	/** given an array containing e.g. "a", "b", with separator "," produces "a,b" */
	public static String join(Object[] list, String seperator) {
		boolean app = false;
		StringBuilder out = new StringBuilder();
		for (Object s: list) {
			if (app) out.append(seperator);
			out.append(s);
			app = true;
		}
		return out.toString();
	}
    
    /** replaces all key->value entries from the replacement map in source (non-regex) */
    @SuppressWarnings("rawtypes")
    public static String replaceAll(String source, Map replacements) {
        for (Object rr: replacements.entrySet()) {
            Map.Entry r = (Map.Entry)rr;
            source = replaceAllNonRegex(source, ""+r.getKey(), ""+r.getValue());
        }
        return source;
    }
    
    /** NON-REGEX replaceAll -
     * replaces all instances in source, of the given pattern, with the given replacement
     * (not  interpreting any arguments as regular expressions)
     */
    public static String replaceAll(String source, String pattern, String replacement) {
        if (source==null) return source;
        StringBuilder result = new StringBuilder(source.length());
        for (int i=0; i<source.length(); ) {
            if (source.substring(i).startsWith(pattern)) {
                result.append(replacement);
                i += pattern.length();
            } else {
                result.append(source.charAt(i));
                i++;
            }
        }
        return result.toString();
    }
    
    /** NON-REGEX replacement -- explicit method name for reabaility, doing same as Strings.replaceAll */
    public static String replaceAllNonRegex(String source, String pattern, String replacement) {
        return replaceAll(source, pattern, replacement);
    }

    /** REGEX replacement -- explicit method name for reabaility, doing same as String.replaceAll */
    public static String replaceAllRegex(String source, String pattern, String replacement) {
        return source.replaceAll(pattern, replacement);
    }

	public static final String VALID_NON_ALPHANUM_FILE_CHARS = " -_.";

	/** returns a valid filename based on 's'; throws exception if no valid filename can be formed;
	 *  valid filename looks to start with first alphanum char, then include all alphanum char plus
	 *  those in VALID_NON_ALPHANUM_FILE_CHARS */
	public static String makeValidFilename(String s) {
		if (s==null) throw new NullPointerException("Cannot make valid filename from null string");
		StringBuilder sb = new StringBuilder();
		char c[] = s.toCharArray();
		int i=0;
		while (i<c.length && !Character.isLetterOrDigit(c[i])) i++;
		if (i>=c.length) throw new IllegalArgumentException("Cannot make valid filename from string '"+s+"'");
		while (i<c.length) {
			if (Character.isLetterOrDigit(c[i])) sb.append(c[i]);
			else if (VALID_NON_ALPHANUM_FILE_CHARS.indexOf(c[i])>=0) sb.append(c[i]);
			i++;
		}
		return sb.toString();
	}

	/** returns a valid java type name based on the given string;
	 * removes certain chars (like apostrophe), replaces 1+ non-java chars by _,
	 * and prepends _ if the first char is only valid as an identifier part (not start);
	 * if all chars are invalid, returns "__hash"+s.hashCode()
	 * <p>
	 * means result is usually unique to s, though this isn't guaranteed */
	public static String makeValidJavaName(String s) {
		if (s==null) return "__null";
		if (s.length()==0) return "_";
		StringBuilder sb = new StringBuilder();
		boolean lastWas_ = false;
		for (char c : s.toCharArray()) {
			if (sb.length()==0) {
				if (Character.isJavaIdentifierStart(c)) sb.append(c);
				else if (Character.isJavaIdentifierPart(c)) {
					sb.append('_');
					sb.append(c);
				} else {
					sb.append('_');
					lastWas_ = true;
				}
			} else {
				if (Character.isJavaIdentifierPart(c)) {
					sb.append(c);
					lastWas_ = false;
				} else if (c=='\'') ;
				else {
					if (!lastWas_) sb.append('_');
					lastWas_ = true;
				}
			}
		}
		if (sb.toString().equals("_")) return "__hash"+s.hashCode();
		return sb.toString();
	}

	/** returns a valid java type name based on the given string,
	 * translated as per makeValidJavaName, but with hashcode appended
	 * where necessary to guarantee uniqueness (for all but simple strings)
	 **/
	public static String makeValidUniqueJavaName(String s) {
		if (s==null) return "__null";
		if (s.length()==0) return "__empty";
		StringBuilder sb = new StringBuilder();
		boolean lastWas_ = false;
		boolean needsHashCode = false;
		for (char c : s.toCharArray()) {
			if (sb.length()==0) {
				if (Character.isJavaIdentifierStart(c)) sb.append(c);
				else if (Character.isJavaIdentifierPart(c)) {
					needsHashCode = true;
					sb.append('_');
					sb.append(c);
				} else {
					needsHashCode = true;
					sb.append('_');
					lastWas_ = true;
				}
			} else {
				if (Character.isJavaIdentifierPart(c)) {
					sb.append(c);
					lastWas_ = false;
				} else if (c=='\'') {
					needsHashCode = true;
				} else {
					if (!lastWas_) {
						if (c!=' ') needsHashCode = true;
						sb.append('_');
						lastWas_ = true;
					} else {
						needsHashCode = true;
					}
				}
			}
		}
		return sb.toString()+(needsHashCode ? "_"+s.hashCode() : "");
	}

	/** provided for convenience, see {@link Identifiers#makeRandomId(int) }*/
	public static String makeRandomId(int l) {
	    return Identifiers.makeRandomId(l);
	}

	/** pads the string with 0's at the left up to len; no padding if i longer than len */
	public static String makeZeroPaddedString(int i, int len) {
		return makePaddedString(""+i, len, "0", "");
	}

	/** pads the string with "pad" at the left up to len; no padding if base longer than len */
	public static String makePaddedString(String base, int len, String left_pad, String right_pad) {
		String s = ""+(base==null ? "" : base);
		while (s.length()<len) s=left_pad+s+right_pad;
		return s;
	}

	public static void trimAll(String[] s) {
		for (int i=0; i<s.length; i++)
			s[i] = (s[i]==null ? "" : s[i].trim());
	}

	/** creates a string from a real number, with specified accuracy (more iff it comes for free, ie integer-part);
	 * switches to E notation if needed to fit within maxlen; can be padded left up too (not useful)
	 * @param x number to use
	 * @param maxlen maximum length for the numeric string, if possible (-1 to suppress)
	 * @param prec number of digits accuracy desired (more kept for integers)
	 * @param leftPadLen will add spaces at left if necessary to make string this long (-1 to suppress) [probably not usef]
	 * @return such a string
	 */
	public static String makeRealString(double x, int maxlen, int prec, int leftPadLen) {
		return makeRealString(x, maxlen, prec, leftPadLen, 0.00000000001, true);
	}
	/** creates a string from a real number, with specified accuracy (more iff it comes for free, ie integer-part);
	 * switches to E notation if needed to fit within maxlen; can be padded left up too (not useful)
	 * @param x number to use
	 * @param maxlen maximum length for the numeric string, if possible (-1 to suppress)
	 * @param prec number of digits accuracy desired (more kept for integers)
	 * @param leftPadLen will add spaces at left if necessary to make string this long (-1 to suppress) [probably not usef]
	 * @param skipDecimalThreshhold if positive it will not add a decimal part if the fractional part is less than this threshhold
	 *    (but for a value 3.00001 it would show zeroes, e.g. with 3 precision and positive threshhold <= 0.00001 it would show 3.00);
	 *    if zero or negative then decimal digits are always shown
	 * @param useEForSmallNumbers whether to use E notation for numbers near zero
	 * @return such a string
	 */
	public static String makeRealString(double x, int maxlen, int prec, int leftPadLen, double skipDecimalThreshhold, boolean useEForSmallNumbers) {
		NumberFormat df = DecimalFormat.getInstance();		
		//df.setMaximumFractionDigits(maxlen);
		df.setMinimumFractionDigits(0);
		//df.setMaximumIntegerDigits(prec);
		df.setMinimumIntegerDigits(1);
		df.setGroupingUsed(false);
		String s;
		if (x==0) {
			if (skipDecimalThreshhold>0 || prec<=1) s="0";
			else {
				s="0.0";
				while (s.length()<prec+1) s+="0";
			}
		} else {
//			long bits= Double.doubleToLongBits(x);
//			int s = ((bits >> 63) == 0) ? 1 : -1;
//			int e = (int)((bits >> 52) & 0x7ffL);
//			long m = (e == 0) ?
//			(bits & 0xfffffffffffffL) << 1 :
//			(bits & 0xfffffffffffffL) | 0x10000000000000L;
//			//s*m*2^(e-1075);
			int log = (int)Math.floor(Math.log10(x));
			int numFractionDigits = (log>=prec ? 0 : prec-log-1);			
			if (numFractionDigits>0) { //need decimal digits
				if (skipDecimalThreshhold>0) { 
					int checkFractionDigits = 0;
					double multiplier = 1;
					while (checkFractionDigits < numFractionDigits) {
						if (Math.abs(x - Math.rint(x*multiplier)/multiplier)<skipDecimalThreshhold)
							break;
						checkFractionDigits++;
						multiplier*=10;
					}
					numFractionDigits = checkFractionDigits;
				}
				df.setMinimumFractionDigits(numFractionDigits);
				df.setMaximumFractionDigits(numFractionDigits);
			} else {
				//x = Math.rint(x);
				df.setMaximumFractionDigits(0);
			}
			s = df.format(x);
			if (maxlen>0 && s.length()>maxlen) {
				//too long:
				double signif = x/Math.pow(10,log);
				if (s.indexOf('.')>=0) {
					//have a decimal point; either we are very small 0.000001
					//or prec is larger than maxlen
					if (Math.abs(x)<1 && useEForSmallNumbers) {
						//very small-- use alternate notation
						s = makeRealString(signif, -1, prec, -1) + "E"+log;
					} else {
						//leave it alone, user error or E not wanted
					}
				} else {
					//no decimal point, integer part is too large, use alt notation
					s = makeRealString(signif, -1, prec, -1) + "E"+log;
				}
			}
		}
		if (leftPadLen>s.length())
			return makePaddedString(s, leftPadLen, " ", "");
		else
			return s;
	}

	/** creates a string from a real number, with specified accuracy (more iff it comes for free, ie integer-part);
	 * switches to E notation if needed to fit within maxlen; can be padded left up too (not useful)
	 * @param x number to use
	 * @param maxlen maximum length for the numeric string, if possible (-1 to suppress)
	 * @param prec number of digits accuracy desired (more kept for integers)
	 * @param leftPadLen will add spaces at left if necessary to make string this long (-1 to suppress) [probably not usef]
	 * @return such a string
	 */
	public static String makeRealStringNearZero(double x, int maxlen, int prec, int leftPadLen) {
		if (Math.abs(x)<0.0000000001) x=0;
		NumberFormat df = DecimalFormat.getInstance();		
		//df.setMaximumFractionDigits(maxlen);
		df.setMinimumFractionDigits(0);
		//df.setMaximumIntegerDigits(prec);
		df.setMinimumIntegerDigits(1);
		df.setGroupingUsed(false);
		String s;
		if (x==0) {
			if (prec<=1) s="0";
			else {
				s="0.0";
				while (s.length()<prec+1) s+="0";
			}
		} else {
//			long bits= Double.doubleToLongBits(x);
//			int s = ((bits >> 63) == 0) ? 1 : -1;
//			int e = (int)((bits >> 52) & 0x7ffL);
//			long m = (e == 0) ?
//			(bits & 0xfffffffffffffL) << 1 :
//			(bits & 0xfffffffffffffL) | 0x10000000000000L;
//			//s*m*2^(e-1075);
			int log = (int)Math.floor(Math.log10(x));
			int scale = (log>=prec ? 0 : prec-log-1);			
			if (scale>0) { //need decimal digits
				double scale10 = Math.pow(10, scale);
				x = Math.rint(x*scale10)/scale10;
				df.setMinimumFractionDigits(scale);
				df.setMaximumFractionDigits(scale);
			} else {
				//x = Math.rint(x);
				df.setMaximumFractionDigits(0);
			}
			s = df.format(x);
			if (maxlen>0 && s.length()>maxlen) {
				//too long:
				double signif = x/Math.pow(10,log);
				if (s.indexOf('.')>=0) {
					//have a decimal point; either we are very small 0.000001
					//or prec is larger than maxlen
					if (Math.abs(x)<1) {
						//very small-- use alternate notation
						s = makeRealString(signif, -1, prec, -1) + "E"+log;
					} else {
						//leave it alone, user error
					}
				} else {
					//no decimal point, integer part is too large, use alt notation
					s = makeRealString(signif, -1, prec, -1) + "E"+log;
				}
			}
		}
		if (leftPadLen>s.length())
			return makePaddedString(s, leftPadLen, " ", "");
		else
			return s;
	}

	
	public static String getLastWord(String s) {
		if (s==null) return null;
		s = s.trim();
		int x = s.lastIndexOf(' ');
		if (x==-1) return s;
		return s.substring(x+1);
	}

	public static String makeTimeString(long utcMillis) {
		return Time.makeTimeString(utcMillis);
	}

	/** returns e.g. { "prefix01", ..., "prefix96" };
	 * see more functional NumericRangeGlobExpander for "prefix{01-96}" 
	 */
	public static String[] makeArray(String prefix, int count) {
		String[] result = new String[count];
		int len = (""+count).length();
		for (int i=1; i<=count; i++)
			result[i-1] = prefix + makePaddedString("", len, "0", ""+i);
		return result;
	}

	public static String[] combineArrays(String[] ...arrays) {
		int totalLen = 0;
		for (String[] array : arrays) {
			if (array!=null) totalLen += array.length;
		}
		String[] result = new String[totalLen];
		int i=0;
		for (String[] array : arrays) {
			if (array!=null) for (String s : array) {
				result[i++] = s;
			}
		}
		return result;
	}

	public static String toInitialCapOnly(String value) {
		if (value==null || value.length()==0) return value;
		return value.substring(0, 1).toUpperCase() + value.substring(1).toLowerCase();
	}

	public static String reverse(String name) {
		return new StringBuffer(name).reverse().toString();
	}

	public static boolean isLowerCase(String s) {
        return s.toLowerCase().equals(s);
    }

    public static String makeRepeated(char c, int length) {
        StringBuilder result = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            result.append(c);
        }
        return result.toString();
    }

    public static String trimEnd(String s) {
        return ("a"+s).trim().substring(1);
    }

    /** returns up to maxlen characters from the start of s */
    public static String maxlen(String s, int maxlen) {
        return s.substring(0, Math.min(s.length(), maxlen));
    }

}
