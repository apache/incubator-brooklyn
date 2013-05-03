package brooklyn.config;

import java.util.Map;

/** convenience extension where map is principally strings or converted to strings
 * (supporting BrooklynProperties) */
public interface StringConfigMap extends ConfigMap {
    /** @see #getFirst(java.util.Map, String...) */
    public String getFirst(String... keys);
    /** returns the value of the first key which is defined
     * <p>
     * takes the following flags:
     * 'warnIfNone' or 'failIfNone' (both taking a boolean (to use default message) or a string (which is the message));
     * and 'defaultIfNone' (a default value to return if there is no such property);
     * defaults to no warning and null default value */
    public String getFirst(@SuppressWarnings("rawtypes") Map flags, String... keys);
}
