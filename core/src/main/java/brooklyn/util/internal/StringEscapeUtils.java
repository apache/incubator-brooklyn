package brooklyn.util.internal;

public class StringEscapeUtils {

	public static String escapeHttpUrl(String url) {
		return url.replaceAll(" ", "%20");
		//TODO quite a few other things disallowed in URL
	}
	
}
