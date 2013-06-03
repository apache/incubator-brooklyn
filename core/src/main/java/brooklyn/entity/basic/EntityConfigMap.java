package brooklyn.entity.basic;

import static brooklyn.util.GroovyJavaMethods.elvis;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.config.ConfigKey.HasConfigKey;
import brooklyn.config.ConfigMap;
import brooklyn.event.basic.StructuredConfigKey;
import brooklyn.management.ExecutionContext;
import brooklyn.util.flags.TypeCoercions;
import brooklyn.util.internal.ConfigKeySelfExtracting;
import brooklyn.util.task.DeferredSupplier;

import com.google.common.base.Predicate;
import com.google.common.collect.Maps;

@SuppressWarnings("deprecation")
public class EntityConfigMap implements ConfigMap {

    private static final Logger LOG = LoggerFactory.getLogger(EntityConfigMap.class);

    /** entity against which config resolution / task execution will occur */
    private final AbstractEntity entity;

    private final ConfigMapViewWithStringKeys mapViewWithStringKeys = new ConfigMapViewWithStringKeys(this);

    /**
     * Map of configuration information that is defined at start-up time for the entity. These
     * configuration parameters are shared and made accessible to the "children" of this
     * entity.
     */
    private final Map<ConfigKey<?>,Object> ownConfig;
    private final Map<ConfigKey<?>,Object> inheritedConfig = Collections.synchronizedMap(new LinkedHashMap<ConfigKey<?>, Object>());

    public EntityConfigMap(AbstractEntity entity, Map<ConfigKey<?>, Object> storage) {
        this.entity = checkNotNull(entity, "entity must be specified");
        this.ownConfig = checkNotNull(storage, "storage map must be specified");
    }

    public <T> T getConfig(ConfigKey<T> key) {
        return getConfig(key, null);
    }
    
    public <T> T getConfig(HasConfigKey<T> key) {
        return getConfig(key.getConfigKey(), null);
    }
    
    public <T> T getConfig(HasConfigKey<T> key, T defaultValue) {
        return getConfig(key.getConfigKey(), defaultValue);
    }
    
    @SuppressWarnings("unchecked")
    public <T> T getConfig(ConfigKey<T> key, T defaultValue) {
        // FIXME What about inherited task in config?!
        //              alex says: think that should work, no?
        // FIXME What if someone calls getConfig on a task, before setting parent app?
        //              alex says: not supported (throw exception, or return the task)
        
        // In case this entity class has overridden the given key (e.g. to set default), then retrieve this entity's key
        // TODO If ask for a config value that's not in our configKeys, should we really continue with rest of method and return key.getDefaultValue?
        //      e.g. SshBasedJavaAppSetup calls setAttribute(JMX_USER), which calls getConfig(JMX_USER)
        //           but that example doesn't have a default...
        ConfigKey<T> ownKey = entity!=null ? (ConfigKey<T>)elvis(entity.getEntityType().getConfigKey(key.getName()), key) : key;
        
        ExecutionContext exec = entity.getExecutionContext();
        
        // Don't use groovy truth: if the set value is e.g. 0, then would ignore set value and return default!
        if (ownKey instanceof ConfigKeySelfExtracting) {
            if (((ConfigKeySelfExtracting<T>)ownKey).isSet(ownConfig)) {
                return ((ConfigKeySelfExtracting<T>)ownKey).extractValue(ownConfig, exec);
            } else if (((ConfigKeySelfExtracting<T>)ownKey).isSet(inheritedConfig)) {
                return ((ConfigKeySelfExtracting<T>)ownKey).extractValue(inheritedConfig, exec);
            }
        } else {
            LOG.warn("Config key {} of {} is not a ConfigKeySelfExtracting; cannot retrieve value; returning default", ownKey, this);
        }
        return TypeCoercions.coerce((defaultValue != null) ? defaultValue : ownKey.getDefaultValue(), key.getType());
    }
    
    @Override
    public Object getRawConfig(ConfigKey<?> key) {
        if (ownConfig.containsKey(key)) return ownConfig.get(key);
        if (inheritedConfig.containsKey(key)) return inheritedConfig.get(key);
        return null;
    }
    
    /** returns the config visible at this entity, local and inherited (preferring local) */
    public Map<ConfigKey<?>,Object> getAllConfig() {
        Map<ConfigKey<?>,Object> result = new LinkedHashMap<ConfigKey<?>,Object>(inheritedConfig.size()+ownConfig.size());
        result.putAll(inheritedConfig);
        result.putAll(ownConfig);
        return Collections.unmodifiableMap(result);
    }

    /** returns the config defined at this entity, ie not inherited */
    public Map<ConfigKey<?>,Object> getLocalConfig() {
        Map<ConfigKey<?>,Object> result = new LinkedHashMap<ConfigKey<?>,Object>(ownConfig.size());
        result.putAll(ownConfig);
        return Collections.unmodifiableMap(result);
    }
    
    public Object setConfig(ConfigKey<?> key, Object v) {
        Object val;
        if ((v instanceof Future) || (v instanceof DeferredSupplier)) {
            // no coercion for these (coerce on exit)
            val = v;
        } else if (key instanceof StructuredConfigKey) {
            // no coercion for these structures (they decide what to do)
            val = v;
        } else {
            try {
                val = TypeCoercions.coerce(v, key.getType());
            } catch (Exception e) {
                throw new IllegalArgumentException("Cannot coerce or set "+v+" to "+key, e);
            }
        }
        Object oldVal;
        if (key instanceof StructuredConfigKey) {
            oldVal = ((StructuredConfigKey)key).applyValueToMap(val, ownConfig);
        } else {
            oldVal = ownConfig.put(key, val);
        }
        entity.refreshInheritedConfigOfChildren();
        return oldVal;
    }
    
    public void setLocalConfig(Map<ConfigKey<?>, ? extends Object> vals) {
        ownConfig.clear();
        ownConfig.putAll(vals);
    }
    
    public void setInheritedConfig(Map<ConfigKey<?>, ? extends Object> vals) {
        inheritedConfig.clear();
        inheritedConfig.putAll(vals);
    }
    
    public void clearInheritedConfig() {
        inheritedConfig.clear();
    }

    @Override
    public EntityConfigMap submap(Predicate<ConfigKey<?>> filter) {
        EntityConfigMap m = new EntityConfigMap(entity, Maps.<ConfigKey<?>, Object>newLinkedHashMap());
        for (Map.Entry<ConfigKey<?>,Object> entry: inheritedConfig.entrySet())
            if (filter.apply(entry.getKey()))
                m.inheritedConfig.put(entry.getKey(), entry.getValue());
        for (Map.Entry<ConfigKey<?>,Object> entry: ownConfig.entrySet())
            if (filter.apply(entry.getKey()))
                m.ownConfig.put(entry.getKey(), entry.getValue());
        return m;
    }

    @Override
    public String toString() {
        return super.toString()+"[own="+Entities.sanitize(ownConfig)+"; inherited="+Entities.sanitize(inheritedConfig)+"]";
    }
    
    public Map<String,Object> asMapWithStringKeys() {
        return mapViewWithStringKeys;
    }
}
