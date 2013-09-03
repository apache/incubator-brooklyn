package brooklyn.event.basic;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.management.ExecutionContext;
import brooklyn.util.collections.Jsonya;
import brooklyn.util.collections.MutableMap;

import com.google.common.collect.Maps;

/** A config key which represents a map, where contents can be accessed directly via subkeys.
 * Items added directly to the map must be of type map, and are put (as individual subkeys). 
 * <p>
 * You can also pass an appropriate {@link MapModification} from {@link MapModifications}
 * to clear (and clear-and-set). */
//TODO Create interface
public class MapConfigKey<V> extends BasicConfigKey<Map<String,V>> implements StructuredConfigKey {
    
    private static final long serialVersionUID = -6126481503795562602L;
    private static final Logger log = LoggerFactory.getLogger(MapConfigKey.class);
    
    public final Class<V> subType;

    public MapConfigKey(Class<V> subType, String name) {
        this(subType, name, name, null);
    }

    public MapConfigKey(Class<V> subType, String name, String description) {
        this(subType, name, description, null);
    }

    // TODO it isn't clear whether defaultValue is an initialValue, or a value to use when map is empty
    // probably the latter, currently ... but maybe better to say that map configs are never null, 
    // and defaultValue is really an initial value
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public MapConfigKey(Class<V> subType, String name, String description, Map<String, V> defaultValue) {
        super((Class)Map.class, name, description, defaultValue);
        this.subType = subType;
    }

    public ConfigKey<V> subKey(String subName) {
        return subKey(subName, "sub-element of " + getName() + ", named " + subName);
    }
    // it is not possible to supply default values
    public ConfigKey<V> subKey(String subName, String description) {
        return new SubElementConfigKey<V>(this, subType, getName() + "." + subName, description, null);
    }

    public boolean isSubKey(Object contender) {
        return contender instanceof ConfigKey && isSubKey((ConfigKey<?>)contender);
    }
    
    public boolean isSubKey(ConfigKey<?> contender) {
        return (contender instanceof SubElementConfigKey && this.equals(((SubElementConfigKey<?>) contender).parent));
    }

    public String extractSubKeyName(ConfigKey<?> subKey) {
        return subKey.getName().substring(getName().length() + 1);
    }

    @Override
    public Map<String,V> extractValue(Map<?,?> vals, ExecutionContext exec) {
        Map<String,V> result = Maps.newLinkedHashMap();
        for (Map.Entry<?,?> entry : vals.entrySet()) {
            Object k = entry.getKey();
            if (isSubKey(k)) {
                @SuppressWarnings("unchecked")
                SubElementConfigKey<V> subk = (SubElementConfigKey<V>) k;
                result.put(extractSubKeyName(subk), (V) subk.extractValue(vals, exec));
            }
        }
        return Collections.unmodifiableMap(result);
    }
    /** returns the entries in the map against this config key and any sub-config-keys, without resolving
     * (like {@link #extractValue(Map, ExecutionContext)} but without resolving/coercing;
     * useful because values in this "map" are actually stored against {@link SubElementConfigKey}s */
    public Map<String,Object> rawValue(Map<?,?> vals) {
        Map<String,Object> result = Maps.newLinkedHashMap();
        for (Map.Entry<?,?> entry : vals.entrySet()) {
            Object k = entry.getKey();
            if (isSubKey(k)) {
                @SuppressWarnings("unchecked")
                SubElementConfigKey<V> subk = (SubElementConfigKey<V>) k;
                result.put(extractSubKeyName(subk), vals.get(subk));
            }
        }
        return result;
    }

    @Override
    public boolean isSet(Map<?, ?> vals) {
        if (vals.containsKey(this))
            return true;
        for (Object contender : vals.keySet()) {
            if (isSubKey(contender)) {
                return true;
            }
        }
        return false;
    }
    
    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    public Object applyValueToMap(Object value, Map target) {
        if (value == null)
            return null;
        if (value instanceof StructuredModification)
            return ((StructuredModification)value).applyToKeyInMap(this, target);
        if (value instanceof Map.Entry)
            return applyEntryValueToMap((Map.Entry)value, target);
        if (!(value instanceof Map)) 
            throw new IllegalArgumentException("Cannot set non-map entries "+value+" on "+this);
        
        Map result = new MutableMap();
        for (Object entry: ((Map)value).entrySet()) {
            Map.Entry entryT = (Map.Entry)entry;
            result.put(entryT.getKey(), applyEntryValueToMap(entryT, target));
        }
        return result;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    protected Object applyEntryValueToMap(Entry value, Map target) {
        Object k = value.getKey();
        if (isSubKey(k)) {
            // do nothing
        } else if (k instanceof String) {
            k = subKey((String)k);
        } else {
            log.warn("Unexpected subkey "+k+" being inserted into "+this+"; ignoring");
            k = null;
        }
        if (k!=null)
            return target.put(k, value.getValue());
        else 
            return null;
    }

    public interface MapModification<V> extends StructuredModification<MapConfigKey<V>>, Map<String,V> {
    }
    
    public static class MapModifications extends StructuredModifications {
        /** when passed as a value to a MapConfigKey, causes each of these items to be put 
         * (this Mod is redundant as no other value is really sensible) */
        public static final <V> MapModification<V> put(final Map<String,V> itemsToPutInMapReplacing) { 
            return new MapModificationBase<V>(itemsToPutInMapReplacing, false);
        }
        /** when passed as a value to a MapConfigKey, causes the map to be cleared and these items added */
        public static final <V> MapModification<V> set(final Map<String,V> itemsToPutInMapAfterClearing) {
            return new MapModificationBase<V>(itemsToPutInMapAfterClearing, true);
        }
        /** when passed as a value to a MapConfigKey, causes the items to be added to the underlying map
         * using {@link Jsonya} add semantics (combining maps and lists) */
        public static final <V> MapModification<V> add(final Map<String,V> itemsToAdd) {
            return new MapModificationBase<V>(itemsToAdd, false /* ignored */) {
                private static final long serialVersionUID = 1L;
                @SuppressWarnings("rawtypes")
                @Override
                public Object applyToKeyInMap(MapConfigKey<V> key, Map target) {
                    return key.applyValueToMap(Jsonya.of(key.rawValue(target)).add(this).getRootMap(), target);
                }
            };
        }
    }

    public static class MapModificationBase<V> extends LinkedHashMap<String,V> implements MapModification<V> {
        private static final long serialVersionUID = -1670820613292286486L;
        private final boolean clearFirst;
        public MapModificationBase(Map<String,V> delegate, boolean clearFirst) {
            super(delegate);
            this.clearFirst = clearFirst;
        }
        @SuppressWarnings({ "rawtypes" })
        @Override
        public Object applyToKeyInMap(MapConfigKey<V> key, Map target) {
            if (clearFirst) {
                StructuredModification<StructuredConfigKey> clearing = StructuredModifications.clearing();
                clearing.applyToKeyInMap(key, target);
            }
            return key.applyValueToMap(new LinkedHashMap<String,V>(this), target);
        }
    }
}
