package brooklyn.util;

/**
 * Conveniences for manipulating strings.
 * <p>
 * Many of these are aligned with apache commons string utils, providing a lightweight variant of that project.
 */
public class StringUtils {

    /** removes the prefix from the start of the string, if present */
    public static String removeStart(String source, String prefix) {
        if (source==null) return source;
        if (source.startsWith(prefix)) return source.substring(prefix.length());
        return source;
    }

    /** removes the suffix from the end of the string, if present */
    public static String removeEnd(String source, String suffix) {
        if (source==null) return source;
        if (source.endsWith(suffix)) return source.substring(0, source.length()-suffix.length());
        return source;
    }
    
    /** replaces all instances in source, of the given pattern, with the given replacement
     * (not interpreting any arguments as regular expressions)
     */
    public static String replace(String source, String pattern, String replacement) {
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
    
}
