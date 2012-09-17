package brooklyn.util.internal

import brooklyn.util.text.StringEscapes
import brooklyn.util.text.StringEscapes.BashStringEscapes;

/** @deprecated since 0.4.0 use BashStringEscapes */
public class StringEscapeUtils extends StringEscapes {
	
	public static String escapeLiteralForDoubleQuotedBash(String arg) {
        BashStringEscapes.escapeLiteralForDoubleQuotedBash(arg);
	}
    
	/** transforms e.g. [ "-Dname=Bob Johnson", "-Dnet.worth=$100" ]  to 
	 * string which _renders_ as "-Dname=Bob Johnson" "-Dnet.worth=\$100"
	 * so it gets picked up as 2 params in java
	 */
	public static String doubleQuoteLiteralsForBash(String... args) {
		args.collect({"\""+escapeLiteralForDoubleQuotedBash(it)+"\""}).join(" ");
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
    
}
