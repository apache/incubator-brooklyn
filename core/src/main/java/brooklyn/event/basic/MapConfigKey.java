package brooklyn.event.basic;

import java.util.Map;

import brooklyn.entity.ConfigKey;
import brooklyn.management.ExecutionContext;

import com.google.common.collect.Maps;

//TODO Create interface
public class MapConfigKey<V> extends BasicConfigKey<Map> {
    public final Class<V> subType;

    public MapConfigKey(Class<V> subType, String name) {
        this(subType, name, name, null);
    }

    public MapConfigKey(Class<V> subType, String name, String description) {
        this(subType, name, description, null);
    }

    public MapConfigKey(Class<V> subType, String name, String description, Map<String, V> defaultValue) {
        super(Map.class, name, description, defaultValue);
        this.subType = subType;
    }

    public ConfigKey<V> subKey(String subName) {
        return new SubElementConfigKey(this, subType, getName() + "." + subName, "sub-element of " + getName() + ", named " + subName, null);
    }

    public boolean isSubKey(Object contender) {
        return contender instanceof ConfigKey && isSubKey((ConfigKey<?>)contender);
    }
    
    public boolean isSubKey(ConfigKey<?> contender) {
        return (contender instanceof SubElementConfigKey && this == ((SubElementConfigKey) contender).parent);
    }

    public String extractSubKeyName(ConfigKey<?> subKey) {
        return subKey.getName().substring(getName().length() + 1);
    }

    @Override
    public Map<String,V> extractValue(Map<?,?> vals, ExecutionContext exec) {
        Map<String,V> result = Maps.newLinkedHashMap();
        for (Map.Entry<?,?> entry : vals.entrySet()) {
            Object k = entry.getKey();
            Object v = entry.getValue();
            if (isSubKey(k)) {
                SubElementConfigKey<?> subk = (SubElementConfigKey<?>) k;
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
}
