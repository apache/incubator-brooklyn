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
        for (String k: (Collection<String>)properties.keySet()) {
            if (k.startsWith(prefix)) {
                result.put(k.substring(prefix.length()), properties.get(k));
            }
        }
        return result;
    }
    
    public static void setConfigFromProperties(Entity entity, Map properties) {
        for (Object k: (Collection)properties.keySet()) {
            ConfigKey key; String keyName;
            if (k instanceof ConfigKey) {
                key = (ConfigKey)k;
                keyName = key.getName();
            } else {
                keyName = ""+k;
                key = new BasicConfigKey(String.class, keyName);
            }
            Object v = properties.get(k);
            ((EntityLocal)entity).setConfig(key, v);
        }
    }
    
    /** returns the map of values which were set */
    public static Map setConfigIfUnset(Entity entity, Map config) {
        Map result = MutableMap.of();
        
        Set<String> knownKeys = new LinkedHashSet<String>();
        Map<ConfigKey, Object> allConfig = ((AbstractEntity)entity).getAllConfig();
        for (ConfigKey k: allConfig.keySet()) knownKeys.add(k.getName());
        
        for (Object k: (Collection)config.keySet()) {
            ConfigKey key; String keyName;
            if (k instanceof ConfigKey) {
                key = (ConfigKey)k;
                keyName = key.getName();
            } else {
                keyName = ""+k;
                key = new BasicConfigKey(Object.class, keyName);
            }
            if (!knownKeys.contains(keyName)) {
                Object v = config.get(k);
                ((EntityLocal)entity).setConfig(key, v);
                result.put(keyName, v);
            }
        }
        
        return result;
    }

}
