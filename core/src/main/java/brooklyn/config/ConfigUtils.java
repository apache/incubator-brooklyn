package brooklyn.config;

import java.io.File;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.util.MutableMap;

@SuppressWarnings({"unchecked","rawtypes"})
public class ConfigUtils {

    public static <T> T getRequiredConfig(Entity entity, ConfigKey<T> key) {
        T result = entity.getConfig(key);
        if (result==null) throw new IllegalStateException("Configuration "+key+" is required");
        return result;
    }
    
    public static BrooklynProperties loadFromFile(String file) {
        BrooklynProperties result = BrooklynProperties.Factory.newEmpty();
        if (file!=null) result.addFrom(new File(file));
        return result;
    }
    
    public static BrooklynProperties filterForPrefix(BrooklynProperties properties, String prefix) {
        BrooklynProperties result = BrooklynProperties.Factory.newEmpty();
        for (String k: (Collection<String>)properties.keySet()) {
            if (k.startsWith(prefix)) {
                result.put(k, properties.get(k));
            }
        }
        return result;
    }
    
    /** prefix generally ends with a full stop */
    public static BrooklynProperties filterForPrefixAndStrip(Map<String,?> properties, String prefix) {
        BrooklynProperties result = BrooklynProperties.Factory.newEmpty();
        for (String k: properties.keySet()) {
            if (k.startsWith(prefix)) {
                result.put(k.substring(prefix.length()), properties.get(k));
            }
        }
        return result;
    }
}
