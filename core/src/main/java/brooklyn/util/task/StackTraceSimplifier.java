package brooklyn.util.task;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * Utility class for cleaning up stacktraces.
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

        for (String s: BLACKLIST){
            if (el.getClassName().startsWith(s))  useful = false;
            if (el.getClassName().replace('_', '.').startsWith(s))  useful = false;
        }

        return useful;
    }

    public static List<StackTraceElement> cleanStackTrace(List<StackTraceElement> st) {
        List<StackTraceElement> result = new LinkedList<StackTraceElement>();
        for (StackTraceElement element: st){
            if (isStackTraceElementUseful(element)){
                result.add(element);
            }
        }

        return result;
    }

    public static StackTraceElement[] cleanStackTrace(StackTraceElement[] st) {
        List<StackTraceElement> result = cleanStackTrace(Arrays.asList(st));
        return result.toArray(new StackTraceElement[result.size()]);
    }
}
