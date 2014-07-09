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

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.util.collections.MutableList;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.net.URLParamEncoder;

public class StringEscapes {

    private static final Logger log = LoggerFactory.getLogger(StringEscapes.class);
    
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
            throw Exceptions.propagate(e);
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
                throw Exceptions.propagate(e);
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
                throw Exceptions.propagate(e);
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
            return null;
        }

        /** given a string in bash notation, e.g. with quoted portions needing unescaped, returns the unescaped and unquoted version */
        public static String unwrapBashQuotesAndEscapes(String s) {
            return applyUnquoteAndUnescape(s, "Bash", true);
        }
    }
    
    
    public static class JavaStringEscapes {
        /** converts normal string to java escaped for double-quotes (but not wrapped in double quotes) */
        public static String escapeJavaString(String value) {
            StringBuilder out = new StringBuilder();
            try {
                escapeJavaString(value, out);
            } catch (IOException e) {
                //shouldn't happen for string builder
                throw Exceptions.propagate(e);
            }
            return out.toString();
        }

        /** converts normal string to java escaped for double-quotes and wrapped in those double quotes */
        public static String wrapJavaString(String value) {
            StringBuilder out = new StringBuilder();
            try {
                wrapJavaString(value, out);
            } catch (IOException e) {
                //shouldn't happen for string builder
                throw Exceptions.propagate(e);
            }
            return out.toString();
        }

        /** as {@link #unwrapJavaString(String)} if the given string is wrapped in double quotes;
         * otherwise just returns the given string */
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

        /** converts normal string to java escaped for double-quotes (but not wrapped in double quotes) */
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
        
        /** converts a comma separated list in a single string to a list of strings, 
         * doing what would be expected if given java or json style string as input,
         * and falling back to returning the input.
         * <p>
         * this method does <b>not</b> throw exceptions on invalid input,
         * but just returns that input
         * <p>
         * specifically, uses the following rules (executed once in sequence:
         * <li> 1) if of form <code>[ X ]</code> (in brackets after trim), 
         *      then removes brackets and applies following rules to X (for any X including quoted or with commas)
         * <li> 2) if of form <code>"X"</code> 
         *      (in double quotes after trim, 
         *      where X contains no internal double quotes unless escaped with backslash) 
         *      then returns list containing X unescaped (\x replaced by x)
         * <li> 3) if of form <code>X</code> or <code>X, Y, ...</code> 
         *      (where X, Y, ... each satisfy the constraint given in 2, or have no double quotes or commas in them)
         *      then returns the concatenation of rule 2 applied to non-empty X, Y, ...
         *      (if you want an empty string in a list, you must double quote it)
         * <li> 4) for any other form X returns [ X ], including empty list for empty string
         * <p>
         * @see #unwrapOptionallyQuotedJavaStringList(String)
         **/
        public static List<String> unwrapJsonishListIfPossible(String input) {
            try {
                return unwrapOptionallyQuotedJavaStringList(input);
            } catch (Exception e) {
                Exceptions.propagateIfFatal(e);
                if (e instanceof IllegalArgumentException) {
                    if (log.isDebugEnabled()) 
                        log.debug("Unable to parse JSON list '"+input+"' ("+e+"); treating as single-element string list");
                } else {
                    log.warn("Unable to parse JSON list '"+input+"' ("+e+"); treating as single-element string list", e);
                }
                return MutableList.of(input);
            }
        }
        
        /** as {@link #unwrapJsonishListIfPossible(String)} but throwing errors 
         * if something which looks like a string or set of brackets is not well-formed
         * (this does the work for that method) 
         * @throws IllegalArgumentException if looks to have quoted list or surrounding brackets but they are not syntactically valid */
        public static List<String> unwrapOptionallyQuotedJavaStringList(String input) {
            if (input==null) return null;
            MutableList<String> result = MutableList.of();
            String i1 = input.trim();
            
            boolean inBrackets = (i1.startsWith("[") && i1.endsWith("]"));
            if (inBrackets) i1 = i1.substring(1, i1.length()-2).trim();
                
            QuotedStringTokenizer qst = new QuotedStringTokenizer(i1, "\"", true, ",", false);
            while (qst.hasMoreTokens()) {
                String t = qst.nextToken().trim();
                if (isWrappedInDoubleQuotes(t))
                    result.add(unwrapJavaString(t));
                else {
                    if (inBrackets && (t.indexOf('[')>=0 || t.indexOf(']')>=0))
                        throw new IllegalArgumentException("Literal square brackets must be quoted, in element '"+t+"'");
                    result.add(t.trim());
                }
            }
            
            return result;
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
