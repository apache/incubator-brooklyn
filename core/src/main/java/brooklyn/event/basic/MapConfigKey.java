package brooklyn.event.basic;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.ConfigKey;
import brooklyn.management.ExecutionContext;
import brooklyn.util.MutableMap;

import com.google.common.collect.Maps;

/** A config key which represents a map, where contents can be accessed directly via subkeys.
 * Items added directly to the map must be of type map, and are put (as individual subkeys). */
//TODO Create interface
//TODO supply mechanism for clearing. supply mechanism for putting a closure which returns multiple values added (but that might be too hard).
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
        return new SubElementConfigKey<V>(this, subType, getName() + "." + subName, "sub-element of " + getName() + ", named " + subName, null);
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
        // other items are added to the list
        if (!(value instanceof Map)) throw new IllegalArgumentException("Cannot set non-map entries "+value+" on "+this);
        Map result = new MutableMap();
        for (Object entry: ((Map)value).entrySet()) {
            Object k = ((Map.Entry)entry).getKey();
            if (isSubKey(k)) {
                // do nothing
            } else if (k instanceof String) {
                k = subKey((String)k);
            } else {
                log.warn("Unexpected subkey "+k+" being inserted into "+this+"; ignoring");
                k = null;
            }
            if (k!=null)
                result.put(k, target.put(k, ((Map.Entry)entry).getValue()));
            else 
                return null;
        }
        return result;
    }

}
