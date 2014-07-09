package brooklyn.entity.basic;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import brooklyn.config.ConfigKey;
import brooklyn.event.basic.BasicConfigKey;

import com.google.common.annotations.Beta;
import com.google.common.collect.Sets;

/**
 * Internal class that presents a view over a ConfigMap, so it looks like a Map (with the
 * keys being the config key names).
 */
@Beta
public class ConfigMapViewWithStringKeys implements Map<String,Object> {

    private brooklyn.config.ConfigMap target;

    public ConfigMapViewWithStringKeys(brooklyn.config.ConfigMap target) {
        this.target = target;
    }
    
    @Override
    public int size() {
        return target.getAllConfig().size();
    }

    @Override
    public boolean isEmpty() {
        return target.getAllConfig().isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return keySet().contains(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return values().contains(value);
    }

    @Override
    public Object get(Object key) {
        return target.getConfig(new BasicConfigKey<Object>(Object.class, (String)key));
    }

    @Override
    public Object put(String key, Object value) {
        throw new UnsupportedOperationException("This view is read-only");
    }

    @Override
    public Object remove(Object key) {
        throw new UnsupportedOperationException("This view is read-only");
    }

    @Override
    public void putAll(Map<? extends String, ? extends Object> m) {
        throw new UnsupportedOperationException("This view is read-only");
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException("This view is read-only");
    }

    @Override
    public Set<String> keySet() {
        LinkedHashSet<String> result = Sets.newLinkedHashSet();
        Set<Map.Entry<ConfigKey<?>, Object>> set = target.getAllConfig().entrySet();
        for (final Map.Entry<ConfigKey<?>, Object> entry: set) {
            result.add(entry.getKey().getName());
        }
        return result;
    }

    @Override
    public Collection<Object> values() {
        return target.getAllConfig().values();
    }

    @Override
    public Set<Map.Entry<String, Object>> entrySet() {
        LinkedHashSet<Map.Entry<String, Object>> result = Sets.newLinkedHashSet();
        Set<Map.Entry<ConfigKey<?>, Object>> set = target.getAllConfig().entrySet();
        for (final Map.Entry<ConfigKey<?>, Object> entry: set) {
            result.add(new Map.Entry<String, Object>() {
                @Override
                public String getKey() {
                    return entry.getKey().getName();
                }

                @Override
                public Object getValue() {
                    return entry.getValue();
                }

                @Override
                public Object setValue(Object value) {
                    return entry.setValue(value);
                }
            });
        }
        return result;
    }
    
}
