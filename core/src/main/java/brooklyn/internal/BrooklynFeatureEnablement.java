package brooklyn.internal;

import java.util.Map;

import com.google.common.collect.Maps;

/**
 * For enabling/disabling experimental features.
 * They can be enabled via java system properties, or by explicitly calling {@link #setEnablement(String, boolean)}.
 * <p>
 * For example, start brooklyn with {@code -Dbrooklyn.experimental.feature.policyPersistence=true}
 * 
 * @author aled
 */
public class BrooklynFeatureEnablement {

    public static final String FEATURE_POLICY_PERSISTENCE_PROPERTY = "brooklyn.experimental.feature.policyPersistence";
    
    public static final String FEATURE_ENRICHER_PERSISTENCE_PROPERTY = "brooklyn.experimental.feature.enricherPersistence";
    
    private static final Map<String, Boolean> FEATURE_ENABLEMENTS = Maps.newLinkedHashMap();

    private static final Object MUTEX = new Object();
    
    static {
        // Idea is here one can put experimental features that are *enabled* by default, but 
        // that can be turned off via system properties. One might want to do that because
        // the feature is deemed risky!
        //   e.g. setDefault(ENABLE_POLICY_PERSISTENCE_PROPERTY, true);
    }
    
    public static boolean isEnabled(String property) {
        synchronized (MUTEX) {
            if (!FEATURE_ENABLEMENTS.containsKey(property)) {
                String rawVal = System.getProperty(property);
                boolean val = Boolean.parseBoolean(rawVal);
                FEATURE_ENABLEMENTS.put(property, val);
            }
            return FEATURE_ENABLEMENTS.get(property);
        }
    }

    public static boolean enable(String property) {
        return setEnablement(property, true);
    }
    
    public static boolean disable(String property) {
        return setEnablement(property, false);
    }
    
    public static boolean setEnablement(String property, boolean val) {
        synchronized (MUTEX) {
            boolean oldVal = isEnabled(property);
            FEATURE_ENABLEMENTS.put(property, val);
            return oldVal;
        }
    }
    
    static void setDefault(String property, boolean val) {
        synchronized (MUTEX) {
            if (!FEATURE_ENABLEMENTS.containsKey(property)) {
                String rawVal = System.getProperty(property);
                if (rawVal == null) {
                    FEATURE_ENABLEMENTS.put(property, val);
                }
            }
        }
    }
    
    static void clearCache() {
        synchronized (MUTEX) {
            FEATURE_ENABLEMENTS.clear();
        }
    }
}
