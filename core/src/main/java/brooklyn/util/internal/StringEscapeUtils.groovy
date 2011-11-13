package brooklyn.util.internal;

public class StringEscapeUtils {

	public static String escapeHttpUrl(String url) {
		return url.replaceAll(" ", "%20");
		//TODO quite a few other things disallowed in URL
	}

	public static String replaceAllNonRegex(String source, String pattern, String replacement) {
		StringBuffer result = [""]
		for (int i=0; i<source.length(); ) {
			if (source.substring(i).startsWith(pattern)) {
				result << replacement
				i += pattern.length()
			} else {
				result << source.substring(i, i+1)
				i++
			}
		}
		return result
	}
	
	public static String replaceAllNonRegex(String source, Map replacements) {
		replacements.inject(source, { input,entry -> replaceAllNonRegex(input, entry.key, entry.value) })
	}
	
	// see SshBasedJavaAppSetup
	public static String escapeLiteralForDoubleQuotedBash(String arg) {
		return replaceAllNonRegex(arg, ["\\":"\\\\", "\"":"\\\"", "\$":"\\\$", "`":"\\`"]);
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
		//double quotes must be preceded by a backslash (preceded by 0 or more bash-escaped backslashes)
		!x.matches(	"[^"+BACKSLASH+DOUBLE_QUOTE+"]*"+
			"("+BACKSLASH+BACKSLASH+")*"+
			DOUBLE_QUOTE+".*")
	}
	
}
