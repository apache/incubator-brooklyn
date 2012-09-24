package brooklyn.event.basic;

import java.util.Map;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.management.ExecutionContext;
import brooklyn.util.MutableMap;

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
        return new SubElementConfigKey<V>(this, subType, description, null);
    }

    public boolean isSubKey(Object contender) {
        return contender instanceof ConfigKey && isSubKey((ConfigKey<?>)contender);
    }
    
    public boolean isSubKey(ConfigKey<?> contender) {
        return (contender instanceof SubElementConfigKey && this == ((SubElementConfigKey<?>) contender).parent);
    }

    public String extractSubKeyName(ConfigKey<?> subKey) {
        return subKey.getName().substring(getName().length() + 1);
    }

    @Override
    public Map<String,V> extractValue(Map<?,?> vals, ExecutionContext exec) {
        Map<String,V> result = Maps.newLinkedHashMap();
        for (Map.Entry<?,?> entry : vals.entrySet()) {
            Object k = entry.getKey();
//            Object v = entry.getValue();
            if (isSubKey(k)) {
                @SuppressWarnings("unchecked")
                SubElementConfigKey<V> subk = (SubElementConfigKey<V>) k;
                result.put(extractSubKeyName(subk), (V) subk.extractValue(vals, exec));
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

    public interface MapModification extends StructuredModification<MapConfigKey<?>> {}
    
    @SuppressWarnings("rawtypes")
    public static class MapModifications extends StructuredModifications {
        /** when passed as a value to a MapConfigKey, causes each of these items to be put 
         * (this Mod is redundant as no other value is really sensible) */
        public static final MapModification put(final Map items) { 
            return new MapModification() {
                @Override
                public Object applyToKeyInMap(MapConfigKey<?> key, Map target) {
                    return key.applyValueToMap(items, target);
                }
            };
        }
        /** when passed as a value to a MapConfigKey, causes the map to be cleared and these items added */
        public static final MapModification set(final Map items) { 
            return new MapModification() {
                @Override
                public Object applyToKeyInMap(MapConfigKey<?> key, Map target) {
                    clear().applyToKeyInMap(key, target);
                    put(items).applyToKeyInMap(key, target);
                    return null;
                }
            };
        }
    }

}
