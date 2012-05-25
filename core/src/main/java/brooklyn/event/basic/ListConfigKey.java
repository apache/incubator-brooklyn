package brooklyn.event.basic;

import java.util.List;
import java.util.Map;

import brooklyn.entity.ConfigKey;
import brooklyn.management.ExecutionContext;
import brooklyn.util.internal.LanguageUtils;

import com.google.common.collect.Lists;

//TODO Create interface
public class ListConfigKey<V> extends BasicConfigKey<List> {
    public final Class<V> subType;

    public ListConfigKey(Class<V> subType, String name) {
        this(subType, name, name, null);
    }

    public ListConfigKey(Class<V> subType, String name, String description) {
        this(subType, name, description, null);
    }

    public ListConfigKey(Class<V> subType, String name, String description, List<? extends V> defaultValue) {
        super(List.class, name, description, (List) defaultValue);
        this.subType = subType;
    }

    public ConfigKey<V> subKey() {
        String subName = LanguageUtils.newUid();
        return new SubElementConfigKey(this, subType, getName()+"."+subName, "element of "+getName()+", uid "+subName, null);
    }

    public boolean isSubKey(Object contender) {
        return contender instanceof ConfigKey && isSubKey((ConfigKey<?>)contender);
    }
    
    public boolean isSubKey(ConfigKey<?> contender) {
        return (contender instanceof SubElementConfigKey && this == ((SubElementConfigKey) contender).parent);
    }

    public List<V> extractValue(Map<?,?> vals, ExecutionContext exec) {
        List<V> result = Lists.newArrayList();
        for (Map.Entry<?, ?> entry : vals.entrySet()) {
            Object k = entry.getKey();
            Object v = entry.getValue();
            if (isSubKey(k))
                result.add((V) ((SubElementConfigKey) k).extractValue(vals, exec));
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
}
