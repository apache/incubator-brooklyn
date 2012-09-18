package brooklyn.event.basic;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import brooklyn.entity.ConfigKey;
import brooklyn.management.ExecutionContext;
import brooklyn.util.internal.LanguageUtils;

import com.google.common.collect.Lists;

/** A config key representing a list of values. 
 * If a collection is set on this key, all entries are _added_ to the list.
 * If a value is set on this key, it is _added_ to the list.
 * Specific values can be added in a replaceable way by referring to a subkey.
 */
//TODO Create interface
//TODO supply a mechanism for clearing (see below); optionally allow list to be replaced ?
public class ListConfigKey<V> extends BasicConfigKey<List<? extends V>> implements StructuredConfigKey {
    
    private static final long serialVersionUID = 751024268729803210L;
    
    public final Class<V> subType;

    public ListConfigKey(Class<V> subType, String name) {
        this(subType, name, name, null);
    }

    public ListConfigKey(Class<V> subType, String name, String description) {
        this(subType, name, description, null);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public ListConfigKey(Class<V> subType, String name, String description, List<? extends V> defaultValue) {
        super((Class)List.class, name, description, (List<? extends V>) defaultValue);
        this.subType = subType;
    }

    public ConfigKey<V> subKey() {
        String subName = LanguageUtils.newUid();
        return new SubElementConfigKey<V>(this, subType, getName()+"."+subName, "element of "+getName()+", uid "+subName, null);
    }

    public boolean isSubKey(Object contender) {
        return contender instanceof ConfigKey && isSubKey((ConfigKey<?>)contender);
    }
    
    public boolean isSubKey(ConfigKey<?> contender) {
        return (contender instanceof SubElementConfigKey && this == ((SubElementConfigKey<?>) contender).parent);
    }

    @SuppressWarnings("unchecked")
    public List<V> extractValue(Map<?,?> vals, ExecutionContext exec) {
        List<V> result = Lists.newArrayList();
        for (Map.Entry<?, ?> entry : vals.entrySet()) {
            Object k = entry.getKey();
//            Object v = entry.getValue();
            if (isSubKey(k))
                result.add( ((SubElementConfigKey<V>) k).extractValue(vals, exec) );
        }
        return result;
    }

    @Override
    public boolean isSet(Map<?, ?> vals) {
        if (vals.containsKey(this)) return true;
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
        if (value instanceof Collection) {
            for (Object v: (Collection)value) applyValueToMap(v, target);
        } else {
            // just add to list, using anonymous key
            target.put(subKey(), value);
        }
        return null;
    }
  
    // TODO setting this (in method above) object could be used to clear the list...
//    public static final Object CLEAR = new Object();
    
}
