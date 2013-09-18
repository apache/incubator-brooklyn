package brooklyn.util.javalang;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.util.text.Strings;

import com.google.common.collect.ImmutableSet;

/**
 * Utility class for cleaning up stacktraces.
 */
public class StackTraceSimplifier {

    private static final Logger log = LoggerFactory.getLogger(StackTraceSimplifier.class);
    
    /** comma-separated prefixes (not regexes) */
    public static final String DEFAULT_BLACKLIST_SYSTEM_PROPERTY_NAME = "brooklyn.util.javalang.StackTraceSimplifier.blacklist";
    
    /** @deprecated since 0.6.0 use {@link #DEFAULT_BLACKLIST_SYSTEM_PROPERTY_NAME} */ @Deprecated
    public static final String LEGACY_DEFAULT_BLACKLIST_SYSTEM_PROPERTY_NAME = "groovy.sanitized.stacktraces";
    
    private static final Collection<String> DEFAULT_BLACKLIST;
    
    static {
        ImmutableSet.Builder<String> blacklist = ImmutableSet.builder();
        blacklist.addAll(Arrays.asList(
                System.getProperty(DEFAULT_BLACKLIST_SYSTEM_PROPERTY_NAME,
                        "java.," +
                        "javax.," +
                        "sun.," +
                        "groovy.," +
                        "org.codehaus.groovy.," +
                        "gjdk.groovy.,"
                    ).split("(\\s|,)+")));
        
        String legacyDefaults = System.getProperty(LEGACY_DEFAULT_BLACKLIST_SYSTEM_PROPERTY_NAME);
        if (Strings.isNonBlank(legacyDefaults)) {
            log.warn("Detected ude of legacy system property "+LEGACY_DEFAULT_BLACKLIST_SYSTEM_PROPERTY_NAME);
            blacklist.addAll(Arrays.asList(legacyDefaults.split("(\\s|,)+")));
        }
        
        DEFAULT_BLACKLIST = blacklist.build();
    }
    
    private static final StackTraceSimplifier DEFAULT_INSTACE = newInstance();
    
    private final Collection<String> blacklist;
    
    protected StackTraceSimplifier() {
        this(true);
    }

    protected StackTraceSimplifier(boolean includeDefaultBlacklist, String ...packages) {
        ImmutableSet.Builder<String> blacklistB = ImmutableSet.builder();
        if (includeDefaultBlacklist)
            blacklistB.addAll(DEFAULT_BLACKLIST);
        blacklistB.add(packages);
        blacklist = blacklistB.build();
    }

    public static StackTraceSimplifier newInstance() {
        return new StackTraceSimplifier();
    }

    public static StackTraceSimplifier newInstance(String ...additionalBlacklistPackagePrefixes) {
        return new StackTraceSimplifier(true, additionalBlacklistPackagePrefixes);
    }

    public static StackTraceSimplifier newInstanceExcludingOnly(String ...blacklistPackagePrefixes) {
        return new StackTraceSimplifier(false, blacklistPackagePrefixes);
    }

    /** @return whether the given element is useful, that is, not in the blacklist */
    public boolean isUseful(StackTraceElement el) {
        for (String s: blacklist){
            if (el.getClassName().startsWith(s)) return false;;
            // gets underscores in some contexts ?
            if (el.getClassName().replace('_', '.').startsWith(s)) return false;
        }

        return true;
    }

    /** @return new list containing just the {@link #isUseful(StackTraceElement)} stack trace elements */
    public List<StackTraceElement> clean(Iterable<StackTraceElement> st) {
        List<StackTraceElement> result = new LinkedList<StackTraceElement>();
        for (StackTraceElement element: st){
            if (isUseful(element)){
                result.add(element);
            }
        }

        return result;
    }

    /** @return new array containing just the {@link #isUseful(StackTraceElement)} stack trace elements */
    public StackTraceElement[] clean(StackTraceElement[] st) {
        List<StackTraceElement> result = clean(Arrays.asList(st));
        return result.toArray(new StackTraceElement[result.size()]);
    }

    /** @return first {@link #isUseful(StackTraceElement)} stack trace elements, or null */
    public StackTraceElement firstUseful(StackTraceElement[] st) {
        return nthUseful(0, st);
    }

    /** @return (n+1)th {@link #isUseful(StackTraceElement)} stack trace elements (ie 0 is {@link #firstUseful(StackTraceElement[])}), or null */
    public StackTraceElement nthUseful(int n, StackTraceElement[] st) {
        for (StackTraceElement element: st){
            if (isUseful(element)) {
                if (n==0) 
                    return element;
                n--;
            }
        }        
        return null;
    }

    /** {@link #clean(StackTraceElement[])} the given throwable instance, returning the same instance for convenience */
    public <T extends Throwable> T cleaned(T t) {
        t.setStackTrace(clean(t.getStackTrace()));
        return t;
    }

    // ---- statics
    
    /** static convenience for {@link #isUseful(StackTraceElement)} */
    public static boolean isStackTraceElementUseful(StackTraceElement el) {
        return DEFAULT_INSTACE.isUseful(el);
    }

    /** static convenience for {@link #clean(Iterable)} */
    public static List<StackTraceElement> cleanStackTrace(Iterable<StackTraceElement> st) {
        return DEFAULT_INSTACE.clean(st);
    }

    /** static convenience for {@link #clean(StackTraceElement[])} */
    public static StackTraceElement[] cleanStackTrace(StackTraceElement[] st) {
        return DEFAULT_INSTACE.clean(st);
    }

    /** static convenience for {@link #cleaned(Throwable)} */
    public static <T extends Throwable> T cleanedStackTrace(T t) {
        return DEFAULT_INSTACE.cleaned(t);
    }
    
}
