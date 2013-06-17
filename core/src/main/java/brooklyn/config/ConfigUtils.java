package brooklyn.config;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey.HasConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.util.exceptions.Exceptions;

@SuppressWarnings({"unchecked"})
public class ConfigUtils {

    private static final Logger log = LoggerFactory.getLogger(ConfigUtils.class);
    
    public static <T> T getRequiredConfig(Entity entity, ConfigKey<T> key) {
        T result = entity.getConfig(key);
        if (result==null) throw new IllegalStateException("Configuration "+key+" is required");
        return result;
    }
    
    /** prepends the given prefix to the key.  prefix will typically end with a ".".
     * this is useful for configuration purposes when a subsystem uses a short-name config (e.g. "user")
     * but in entity config or at the root (brooklyn.properties) there are longer names (e.g. "brooklyn.ssh.config.user"),
     * and we wish to convert from the shorter names to the longer names. */
    public static <T> ConfigKey<T> prefixedKey(String prefix, ConfigKey<T> key) {
        return ConfigKeys.newPrefixedKey(prefix, key);
    }
    
    /** removes the given prefix from the key for configuration purposes; logs warning and does nothing if there is no such prefix.
     * prefix will typically end with a ".".
     * this is useful for configuration purposes when a subsystem uses a short-name config (e.g. "user")
     * but in entity config or at the root (brooklyn.properties) there are longer names (e.g. "brooklyn.ssh.config.user"),
     * and we wish to convert from the longer names to the short-name. */
    public static <T> ConfigKey<T> unprefixedKey(String prefix, ConfigKey<T> key) {
        String newName = key.getName();
        if (newName.startsWith(prefix)) newName = newName.substring(prefix.length());
        else log.warn("Cannot remove prefix "+prefix+" from key "+key+" (ignoring)");
        return new BasicConfigKey<T>(key.getTypeToken(), newName, key.getDescription(), key.getDefaultValue());
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

    @SuppressWarnings("rawtypes")
    public static Set<HasConfigKey<?>> getStaticKeysOnClass(Class<?> type) {
        Set<HasConfigKey<?>> result = new LinkedHashSet<ConfigKey.HasConfigKey<?>>();
        try {
            for (Field f: type.getFields()) {
                if ((f.getModifiers() & Modifier.STATIC)==0)
                    continue;
                if (ConfigKey.class.isAssignableFrom(f.getType()))
                    result.add(new WrappedConfigKey((ConfigKey<?>) f.get(null)));
                else if (HasConfigKey.class.isAssignableFrom(f.getType()))
                    result.add((HasConfigKey<?>) f.get(null));
            }
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
        return Collections.unmodifiableSet(result);
    }

}
