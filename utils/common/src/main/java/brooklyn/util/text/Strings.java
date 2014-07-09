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
package brooklyn.util.text;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.StringTokenizer;

import javax.annotation.Nullable;

import brooklyn.util.collections.MutableMap;
import brooklyn.util.time.Time;

import com.google.common.base.CharMatcher;
import com.google.common.base.Functions;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.Ordering;

public class Strings {

    /**
     * Checks if the given string is null or is an empty string.
     * Useful for pre-String.isEmpty.  And useful for StringBuilder etc.
     *
     * @param s the String to check
     * @return true if empty or null, false otherwise.
     *
     * @see #isNonEmpty(CharSequence)
     * @see #isBlank(CharSequence)
     * @see #isNonBlank(CharSequence)
     */
    public static boolean isEmpty(CharSequence s) {
        // Note guava has com.google.common.base.Strings.isNullOrEmpty(String),
        // but that is just for String rather than CharSequence
        return s == null || s.length()==0;
    }

    /**
     * Checks if the given string is empty or only consists of whitespace.
     *
     * @param s the String to check
     * @return true if blank, empty or null, false otherwise.
     *
     * @see #isEmpty(CharSequence)
     * @see #isNonEmpty(CharSequence)
     * @see #isNonBlank(CharSequence)
     */
    public static boolean isBlank(CharSequence s) {
        return isEmpty(s) || CharMatcher.WHITESPACE.matchesAllOf(s);
    }

    /**
     * The inverse of {@link #isEmpty(CharSequence)}.
     *
     * @param s the String to check
     * @return true if non empty, false otherwise.
     *
     * @see #isEmpty(CharSequence)
     * @see #isBlank(CharSequence)
     * @see #isNonBlank(CharSequence)
     */
    public static boolean isNonEmpty(CharSequence s) {
        return !isEmpty(s);
    }

    /**
     * The inverse of {@link #isBlank(CharSequence)}.
     *
     * @param s the String to check
     * @return true if non blank, false otherwise.
     *
     * @see #isEmpty(CharSequence)
     * @see #isNonEmpty(CharSequence)
     * @see #isBlank(CharSequence)
     */
    public static boolean isNonBlank(CharSequence s) {
        return !isBlank(s);
    }

    /** throws IllegalArgument if string not empty; cf. guava Preconditions.checkXxxx */
    public static void checkNonEmpty(CharSequence s) {
        if (s==null) throw new IllegalArgumentException("String must not be null");
        if (s.length()==0) throw new IllegalArgumentException("String must not be empty");
    }
    /** throws IllegalArgument if string not empty; cf. guava Preconditions.checkXxxx */
    public static void checkNonEmpty(CharSequence s, String message) {
        if (isEmpty(s)) throw new IllegalArgumentException(message);
    }

	/** removes the first suffix in the list which is present at the end of string
	 * and returns that string; ignores subsequent suffixes if a matching one is found;
	 * returns the original string if no suffixes are at the end
	 */
	public static String removeFromEnd(String string, String ...suffixes) {
	    if (isEmpty(string)) return string;
		for (String suffix : suffixes)
			if (suffix!=null && string.endsWith(suffix)) return string.substring(0, string.length() - suffix.length());
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

    /** convenience for {@link com.google.common.base.Joiner} */
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
	/** convenience for {@link com.google.common.base.Joiner} */
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
	
	/** convenience for joining lines together */
	public static String lines(String ...lines) {
	    return Joiner.on("\n").join(Arrays.asList(lines));
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

    /** Valid non alphanumeric characters for filenames. */
    public static final String VALID_NON_ALPHANUM_FILE_CHARS = "-_.";

    /** 
     * Returns a valid filename based on the input. 
     *
     * A valid filename starts with the first alphanumeric character, then include
     * all alphanumeric characters plus those in {@link #VALID_NON_ALPHANUM_FILE_CHARS},
     * with any runs of invalid characters being replaced by {@literal _}.
     * 
     * @throws NullPointerException if the input string is null.
     * @throws IllegalArgumentException if the input string is blank.
     */
    public static String makeValidFilename(String s) {
        Preconditions.checkNotNull(s, "Cannot make valid filename from null string");
        Preconditions.checkArgument(isNonBlank(s), "Cannot make valid filename from blank string");
        return CharMatcher.anyOf(VALID_NON_ALPHANUM_FILE_CHARS).or(CharMatcher.JAVA_LETTER_OR_DIGIT)
                .negate()
                .trimAndCollapseFrom(s, '_');
    }

    /**
     * A {@link CharMatcher} that matches valid Java identifier characters.
     *
     * @see Character#isJavaIdentifierPart(char)
     */
    public static final CharMatcher IS_JAVA_IDENTIFIER_PART = CharMatcher.forPredicate(new Predicate<Character>() {
        @Override
        public boolean apply(@Nullable Character input) {
            return input != null && Character.isJavaIdentifierPart(input);
        }
    });

    /**
     * Returns a valid Java identifier name based on the input.
     * 
     * Removes certain characterss (like apostrophe), replaces one or more invalid
     * characterss with {@literal _}, and prepends {@literal _} if the first character
     * is only valid as an identifier part (not start).
     * <p>
     * The result is usually unique to s, though this isn't guaranteed, for example if
     * all characters are invalid. For a unique identifier use {@link #makeValidUniqueJavaName(String)}.
     *
     * @see #makeValidUniqueJavaName(String)
     */
    public static String makeValidJavaName(String s) {
        if (s==null) return "__null";
        if (s.length()==0) return "__empty";
        String name = IS_JAVA_IDENTIFIER_PART.negate().collapseFrom(CharMatcher.is('\'').removeFrom(s), '_');
        if (!Character.isJavaIdentifierStart(s.charAt(0))) return "_" + name;
        return name;
    }

    /**
     * Returns a unique valid java identifier name based on the input.
     * 
     * Translated as per {@link #makeValidJavaName(String)} but with {@link String#hashCode()}
     * appended where necessary to guarantee uniqueness.
     *
     * @see #makeValidJavaName(String)
     */
    public static String makeValidUniqueJavaName(String s) {
        String name = makeValidJavaName(s);
        if (isEmpty(s) || IS_JAVA_IDENTIFIER_PART.matchesAllOf(s) || CharMatcher.is('\'').matchesNoneOf(s)) {
            return name;
        } else {
            return name + "_" + s.hashCode();
        }
    }

    /** @see {@link Identifiers#makeRandomId(int)} */
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
	 * @param useEForSmallNumbers whether to use E notation for numbers near zero (e.g. 0.001)
	 * @return such a string
	 */
	public static String makeRealString(double x, int maxlen, int prec, int leftPadLen, double skipDecimalThreshhold, boolean useEForSmallNumbers) {
	    if (x<0) return 
	        "-"+makeRealString(-x, maxlen, prec, leftPadLen);
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
				if (s.indexOf(getDefaultDecimalSeparator())>=0) {
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

	/** returns the first word (whitespace delimited text), or null if there is none (input null or all whitespace) */
    public static String getFirstWord(String s) {
        if (s==null) return null;
        int start = 0;
        while (start<s.length()) {
            if (!Character.isWhitespace(s.charAt(start)))
                break;
            start++;
        }
        int end = start;
        if (end >= s.length())
            return null;
        while (end<s.length()) {
            if (Character.isWhitespace(s.charAt(end)))
                break;
            end++;
        }
        return s.substring(start, end);
    }
	
    /** returns the last word (whitespace delimited text), or null if there is none (input null or all whitespace) */
	public static String getLastWord(String s) {
        if (s==null) return null;
        int end = s.length()-1;
        while (end >= 0) {
            if (!Character.isWhitespace(s.charAt(end)))
                break;
            end--;
        }
        int start = end;
        if (start < 0)
            return null;
        while (start >= 0) {
            if (Character.isWhitespace(s.charAt(start)))
                break;
            start--;
        }
        return s.substring(start+1, end+1);
	}

    /** returns the first word after the given phrase, or null if no such phrase;
     * if the character immediately after the phrase is not whitespace, the non-whitespace 
     * sequence starting with that character will be returned */
    public static String getFirstWordAfter(String context, String phrase) {
        if (context==null || phrase==null) return null;
        int index = context.indexOf(phrase);
        if (index<0) return null;
        return getFirstWord(context.substring(index + phrase.length()));
    }

	/** @deprecated use {@link Time#makeTimeStringRounded(long)} */
	@Deprecated
	public static String makeTimeString(long utcMillis) {
		return Time.makeTimeStringRounded(utcMillis);
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

    public static String trim(String s) {
        if (s==null) return null;
        return s.trim();
    }

    public static String trimEnd(String s) {
        if (s==null) return null;
        return ("a"+s).trim().substring(1);
    }

    /** returns up to maxlen characters from the start of s */
    public static String maxlen(String s, int maxlen) {
        if (s==null) return null;
        return s.substring(0, Math.min(s.length(), maxlen));
    }

    /** returns toString of the object if it is not null, otherwise null */
    public static String toString(Object o) {
        if (o==null) return null;
        return o.toString();
    }

    public static boolean containsLiteralIgnoreCase(CharSequence input, CharSequence fragment) {
        if (input==null) return false;
        if (isEmpty(fragment)) return true;
        int lastValidStartPos = input.length()-fragment.length();
        char f0u = Character.toUpperCase(fragment.charAt(0));
        char f0l = Character.toLowerCase(fragment.charAt(0));
        i: for (int i=0; i<=lastValidStartPos; i++) {
            char ii = input.charAt(i);
            if (ii==f0l || ii==f0u) {
                for (int j=1; j<fragment.length(); j++) {
                    if (Character.toLowerCase(input.charAt(i+j))!=Character.toLowerCase(fragment.charAt(j)))
                        continue i;
                }
                return true;
            }
        }
        return false;
    }

    public static boolean containsLiteral(CharSequence input, CharSequence fragment) {
        if (input==null) return false;
        if (isEmpty(fragment)) return true;
        int lastValidStartPos = input.length()-fragment.length();
        char f0 = fragment.charAt(0);
        i: for (int i=0; i<=lastValidStartPos; i++) {
            char ii = input.charAt(i);
            if (ii==f0) {
                for (int j=1; j<fragment.length(); j++) {
                    if (input.charAt(i+j)!=fragment.charAt(j))
                        continue i;
                }
                return true;
            }
        }
        return false;
    }

    /** Returns a size string using metric suffixes from {@link ByteSizeStrings#metric()}, e.g. 23.5MB */
    public static String makeSizeString(long sizeInBytes) {
        return ByteSizeStrings.metric().makeSizeString(sizeInBytes);
    }

    /** Returns a size string using ISO suffixes from {@link ByteSizeStrings#iso()}, e.g. 23.5MiB */
    public static String makeISOSizeString(long sizeInBytes) {
        return ByteSizeStrings.iso().makeSizeString(sizeInBytes);
    }

    /** Returns a size string using Java suffixes from {@link ByteSizeStrings#java()}, e.g. 23m */
    public static String makeJavaSizeString(long sizeInBytes) {
        return ByteSizeStrings.java().makeSizeString(sizeInBytes);
    }

    /** returns a configurable shortener */
    public static StringShortener shortener() {
        return new StringShortener();
    }

    public static Supplier<String> toStringSupplier(Object src) {
        return Suppliers.compose(Functions.toStringFunction(), Suppliers.ofInstance(src));
    }

    /** wraps a call to {@link String#format(String, Object...)} in a toString, i.e. using %s syntax,
     * useful for places where we want deferred evaluation 
     * (e.g. as message to {@link Preconditions} to skip concatenation when not needed) */
    public static FormattedString format(String pattern, Object... args) {
        return new FormattedString(pattern, args);
    }

    /** returns "s" if the argument is not 1, empty string otherwise; useful when constructing plurals */
    public static String s(int count) {
        return count==1 ? "" : "s";
    }
    /** as {@link #s(int)} based on size of argument */
    public static String s(@Nullable Map<?,?> x) {
        return s(x==null ? 0 : x.size());
    }
    /** as {@link #s(int)} based on size of argument */
    public static String s(Iterable<?> x) {
        if (x==null) return s(0);
        return s(x.iterator());
    }
    /** as {@link #s(int)} based on size of argument */
    public static String s(Iterator<?> x) {
        int count = 0;
        if (x==null || !x.hasNext()) {}
        else { 
            x.next(); count++;
            if (x.hasNext()) count++;
        }
        return s(count);
    }

    /** returns "ies" if the argument is not 1, "y" otherwise; useful when constructing plurals */
    public static String ies(int count) {
        return count==1 ? "y" : "ies";
    }
    /** as {@link #ies(int)} based on size of argument */
    public static String ies(@Nullable Map<?,?> x) {
        return ies(x==null ? 0 : x.size());
    }
    /** as {@link #ies(int)} based on size of argument */
    public static String ies(Iterable<?> x) {
        if (x==null) return ies(0);
        return ies(x.iterator());
    }
    /** as {@link #ies(int)} based on size of argument */
    public static String ies(Iterator<?> x) {
        int count = 0;
        if (x==null || !x.hasNext()) {}
        else { 
            x.next(); count++;
            if (x.hasNext()) count++;
        }
        return ies(count);
    }

    /** converts a map of any objects to a map of strings, preserving nulls and invoking toString where needed */
    public static Map<String, String> toStringMap(Map<?,?> map) {
        if (map==null) return null;
        Map<String,String> result = MutableMap.<String, String>of();
        for (Map.Entry<?,?> e: map.entrySet()) {
            result.put(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
        }
        return result;
    }

    /** returns base repeated count times */
    public static String repeat(String base, int count) {
        if (base==null) return null;
        StringBuilder result = new StringBuilder();
        for (int i=0; i<count; i++)
            result.append(base);
        return result.toString();
    }

    /** returns comparator which compares based on length, with shorter ones first (and null before that);
     * in event of a tie, it uses the toString order */
    public static Ordering<String> lengthComparator() {
        return Ordering.<Integer>natural().onResultOf(StringFunctions.length()).compound(Ordering.<String>natural()).nullsFirst();
    }

    public static String getFirstLine(String s) {
        int idx = s.indexOf('\n');
        if (idx==-1) return s;
        return s.substring(0, idx);
    }

    /** looks for first section of text in following the prefix and, if present, before the suffix;
     * null if the prefix is not present in the string, and everything after the prefix if suffix is not present in the string;
     * if either prefix or suffix is null, it is treated as the start/end of the string */
    public static String getFragmentBetween(String input, String prefix, String suffix) {
        if (input==null) return null;
        int index;
        if (prefix!=null) {
            index = input.indexOf(prefix);
            if (index==-1) return null;
            input = input.substring(index + prefix.length());
        }
        if (suffix!=null) {
            index = input.indexOf(suffix);
            if (index>=0) input = input.substring(0, index);
        }
        return input;
    }

    public static int getWordCount(String phrase, boolean respectQuotes) {
        if (phrase==null) return 0;
        phrase = phrase.trim();
        if (respectQuotes)
            return new QuotedStringTokenizer(phrase).remainderAsList().size();
        else
            return Collections.list(new StringTokenizer(phrase)).size();
    }
    
    public static char getDecimalSeparator(Locale locale) {
        DecimalFormatSymbols dfs = new DecimalFormatSymbols(locale);
        return dfs.getDecimalSeparator();
    }
    
    public static char getDefaultDecimalSeparator() {
        return getDecimalSeparator(Locale.getDefault());
    }

}
