package brooklyn.util.task

/**
 */
public class StackTraceSimplifier {

    private static final String[] BLACKLIST =
        System.getProperty("groovy.sanitized.stacktraces",
            "groovy.," +
            "org.codehaus.groovy.," +
            "java.," +
            "javax.," +
            "sun.," +
            "gjdk.groovy.,"
        ).split("(\\s|,)+");

    private StackTraceSimplifier() {
        // Private constructor as this class is not intended to be instantiated
    }

    public static boolean isStackTraceElementUseful(StackTraceElement el) {
        boolean useful = true;
        BLACKLIST.each {
            if (el.className.startsWith(it)) { useful = false }
            if (el.className.replace('_', '.').startsWith(it)) { useful = false }
        }
        return useful;
    }

    public static List<StackTraceElement> cleanStackTrace(List<StackTraceElement> st) {
        return st.findAll { element -> isStackTraceElementUseful(element) }
    }
    public static StackTraceElement[] cleanStackTrace(StackTraceElement[] st) {
        cleanStackTrace(st as List) as StackTraceElement[];
    }

}
