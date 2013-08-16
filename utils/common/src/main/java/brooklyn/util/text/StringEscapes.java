package brooklyn.util.text;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import brooklyn.util.net.URLParamEncoder;

import com.google.common.base.Throwables;

public class StringEscapes {

    /** if s is wrapped in double quotes containing no unescaped double quotes */
    public static boolean isWrappedInDoubleQuotes(String s) {
        if (Strings.isEmpty(s)) return false;
        if (!s.startsWith("\"") || !s.endsWith("\"")) return false;
        return (s.substring(1, s.length()-1).replace("\\\\", "").replace("\\\"", "").indexOf("\"")==-1);
    }

    /** if s is wrapped in single quotes containing no unescaped single quotes */
    public static boolean isWrappedInSingleQuotes(String s) {
        if (Strings.isEmpty(s)) return false;
        if (!s.startsWith("\'") || !s.endsWith("\'")) return false;
        return (s.substring(1, s.length()-1).replace("\\\\", "").replace("\\\'", "").indexOf("\'")==-1);
    }

    /** if s is wrapped in single or double quotes containing no unescaped quotes of that type */
    public static boolean isWrappedInMatchingQuotes(String s) {
        return isWrappedInDoubleQuotes(s) || isWrappedInSingleQuotes(s);
    }

    /**
     * Encodes a string suitable for use as a parameter in a URL.
     */
    public static String escapeUrlParam(String input) {
        return URLParamEncoder.encode(input);
    }

    /** 
     * Encodes a string suitable for use as a URL in an HTML form: space to +, and high-numbered chars assuming UTF-8.
     * However, it will also convert the first "http://" to "http%3A%2F%2F" so is not suitable for converting an 
     * entire URL.
     * 
     * Also note that parameter-conversion doesn't work in way you'd expect when trying to create a "normal" url.
     * See http://stackoverflow.com/questions/724043/http-url-address-encoding-in-java
     * 
     * @see escapeUrlParam(String), and consider using that instead.
     */
    public static String escapeHtmlFormUrl(String url) {
        try {
            return URLEncoder.encode(url, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw Throwables.propagate(e);
        }
    }

    /** encodes a string to SQL, that is ' becomes '' */
    public static String escapeSql(String x) {
        //identical to apache commons StringEscapeUtils.escapeSql
        if (x==null) return null;
        return x.replaceAll("'", "''");
    }
    
    
    
    public static class BashStringEscapes {
        // single quotes don't permit escapes!  e.g. echo 'hello \' world'    doesn't work
        
        /** wraps plain text in double quotes escaped for use in bash double-quoting */
        public static String wrapBash(String value) {
            StringBuilder out = new StringBuilder();
            try {
                wrapBash(value, out);
            } catch (IOException e) {
                //shouldn't happen for string buffer
                throw Throwables.propagate(e);
            }
            return out.toString();
        }

        /** @see #wrapBash(String) */
        public static void wrapBash(String value, Appendable out) throws IOException {
            out.append('"');
            escapeLiteralForDoubleQuotedBash(value, out);
            out.append('"');
        }

        private static void escapeLiteralForDoubleQuotedBash(String value, Appendable out) throws IOException {
            for (int i=0; i<value.length(); i++) {
                char c = value.charAt(i);
                if (c=='\\' || c=='\"' || c=='$' || c=='`') {
                    appendEscaped(out, c);
                } else if (c == '!') {
                    out.append("\"'!'\"");
                } else {
                    out.append(c);
                }
            }
        }

        /** performs replacements on a string so that it can be legally inserted into a double-quoted bash context 
         * (without the surrounding double quotes; see also {@link #wrapBash(String)}) */
        public static String escapeLiteralForDoubleQuotedBash(String unquotedInputToBeEscaped) {
            StringBuilder out = new StringBuilder();
            try {
                escapeLiteralForDoubleQuotedBash(unquotedInputToBeEscaped, out);
            } catch (IOException e) {
                // shouldn't happen for StringBuilder
                throw Throwables.propagate(e);
            }
            return out.toString();
        }

        /** transforms e.g. [ "-Dname=Bob Johnson", "-Dnet.worth=$100" ]  to 
         * a java string "\"-Dname=Bob Johnson\" \"-Dnet.worth=\$100\"" --
         * which can be inserted into a bash command where it will be picked up as 2 params
         */
        public static String doubleQuoteLiteralsForBash(String... args) {
            StringBuilder result = new StringBuilder();
            for (String arg: args) {
                if (!Strings.isEmpty(result)) result.append(" ");
                result.append("\"");
                result.append(escapeLiteralForDoubleQuotedBash(arg));
                result.append("\"");
            }
            return result.toString();
        }

        //between java and regex parsing, this gives a single backslash and double quote
        private static final String BACKSLASH = "\\\\";
        private static final String DOUBLE_QUOTE = "\\\"";

        public static boolean isValidForDoubleQuotingInBash(String x) {
            return (checkValidForDoubleQuotingInBash(x)==null);
        }

        public static void assertValidForDoubleQuotingInBash(String x) {
            String problem = checkValidForDoubleQuotingInBash(x);
            if (problem==null) return;
            throw new IllegalArgumentException("String \""+x+"\" not acceptable for bash argument (including double quotes): "+problem);
        }

        private static String checkValidForDoubleQuotingInBash(String x) {
            //double quotes must be preceded by a backslash (preceded by 0 or more bash-escaped backslashes)
            if (x.matches(  "[^"+BACKSLASH+DOUBLE_QUOTE+"]*"+
                    "("+BACKSLASH+BACKSLASH+")*"+
                    DOUBLE_QUOTE+".*")) return "unescaped double quote";
            //ampersand also must have odd number of backslashes before it; even number is error
            if (x.matches("[^"+BACKSLASH+BACKSLASH+"]*"+
                    "("+BACKSLASH+BACKSLASH+")*"+
                    "&"+".*")) return "unescaped ampersand";
            return null;
        }

        /** given a string in bash notation, e.g. with quoted portions needing unescaped, returns the unescaped and unquoted version */
        public static String unwrapBashQuotesAndEscapes(String s) {
            return applyUnquoteAndUnescape(s, "Bash", true);
        }
    }
    
    
    public static class JavaStringEscapes {
        public static String escapeJavaString(String value) {
            StringBuilder out = new StringBuilder();
            try {
                escapeJavaString(value, out);
            } catch (IOException e) {
                //shouldn't happen for string builder
                throw Throwables.propagate(e);
            }
            return out.toString();
        }

        public static String wrapJavaString(String value) {
            StringBuilder out = new StringBuilder();
            try {
                wrapJavaString(value, out);
            } catch (IOException e) {
                //shouldn't happen for string builder
                throw Throwables.propagate(e);
            }
            return out.toString();
        }

        public static String unwrapJavaStringIfWrapped(String s) {
            if (!StringEscapes.isWrappedInDoubleQuotes(s)) return s;
            return unwrapJavaString(s);
        }

        /** converts normal string to java escaped for double-quotes and wrapped in those double quotes */
        public static void wrapJavaString(String value, Appendable out) throws IOException {
            out.append('"');
            escapeJavaString(value, out);
            out.append('"');
        }

        public static void escapeJavaString(String value, Appendable out) throws IOException {
            for (int i=0; i<value.length(); i++) {
                char c = value.charAt(i);
                if (c=='\\' || c=='"' || c=='\'') {
                    appendEscaped(out, c);
                } else if (c=='\n') {
                    appendEscaped(out, 'n');
                } else if (c=='\t') {
                    appendEscaped(out, 't');
                } else if (c=='\r') {
                    appendEscaped(out, 'r');
                } else {
                    out.append(c);
                }
            }
        }

        /** given a string in java syntax, e.g. wrapped in quotes and with backslash escapes, returns the literal value,
         * without the surrounding quotes and unescaped; throws IllegalArgumentException if not a valid java string */
        public static String unwrapJavaString(String s) {
            return applyUnquoteAndUnescape(s, "Java", false);
        }
        
        /**
         * Unwraps a sequence of quoted java strings, that are each separated by the given separator.
         * @param trimmedArg
         * @return
         */
        public static List<String> unwrapQuotedJavaStringList(String s, String separator) {
            List<String> result = new ArrayList<String>();
            String remaining = s.trim();
            while (remaining.length() > 0) {
                int endIndex = findNextClosingQuoteOf(remaining);
                result.add(unwrapJavaString(remaining.substring(0, endIndex+1)));
                remaining = remaining.substring(endIndex+1).trim();
                if (remaining.startsWith(separator)) {
                    remaining = remaining.substring(separator.length()).trim();
                } else if (remaining.length() > 0) {
                    throw new IllegalArgumentException("String '"+s+"' has invalid separators, should be '"+separator+"'");
                }
            }
            return result;
        }
        private static int findNextClosingQuoteOf(String s) {
            boolean escaped = false;
            boolean quoted = false;
            for (int i=0; i<s.length(); i++) {
                char c = s.charAt(i);
                if (!quoted) {
                    assert (i==0);
                    assert !escaped;
                    if (c=='"') quoted = true;
                    else throw new IllegalArgumentException("String '"+s+"' is not a valid Java string (must start with double quote)");
                } else {
                    if (escaped) {
                        escaped = false;
                    } else {
                        if (c=='\\') escaped = true;
                        else if (c=='\"') {
                            quoted = false;
                            return i;
                        } 
                    }
                }
            }
            
            assert quoted;
            throw new IllegalArgumentException("String '"+s+"' is not a valid Java string (unterminated string)");
        }
    }
    
    private static void appendEscaped(Appendable out, char c) throws IOException {
        out.append('\\');
        out.append(c);
    }
    private static String applyUnquoteAndUnescape(String s, String mode, boolean allowMultipleQuotes) {
        StringBuilder result = new StringBuilder();
        boolean escaped = false;
        boolean quoted = false;
        for (int i=0; i<s.length(); i++) {
            char c = s.charAt(i);
            if (!quoted) {
                assert (i==0 || allowMultipleQuotes);
                assert !escaped;
                if (c=='"') quoted = true;
                else if (!allowMultipleQuotes)
                    throw new IllegalArgumentException("String '"+s+"' is not a valid "+mode+" string (must start with double quote)");
                else result.append(c);
            } else {
                if (escaped) {
                    if (c=='\\' || c=='"' || c=='\'') result.append(c);
                    else if (c=='n') result.append('\n');
                    else if (c=='t') result.append('\t');
                    else if (c=='r') result.append('\r');
                    else throw new IllegalArgumentException("String '"+s+"' is not a valid "+mode+" string (unsupported escape char '"+c+"' at position "+i+")");
                    escaped = false;
                } else {
                    if (c=='\\') escaped = true;
                    else if (c=='\"') {
                        quoted = false;
                        if (!allowMultipleQuotes && i<s.length()-1)
                            throw new IllegalArgumentException("String '"+s+"' is not a valid "+mode+" string (unescaped interior double quote at position "+i+")");
                    } else result.append(c); 
                }
            }
        }
        if (quoted)
            throw new IllegalArgumentException("String '"+s+"' is not a valid "+mode+" string (unterminated string)");
        assert !escaped;
        return result.toString();
    }
    
}
