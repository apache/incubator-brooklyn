package brooklyn.entity.basic;

import static brooklyn.util.GroovyJavaMethods.elvis;
import groovy.lang.Closure;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.ConfigKey;
import brooklyn.entity.ConfigKey.HasConfigKey;
import brooklyn.management.ExecutionContext;
import brooklyn.util.flags.TypeCoercions;
import brooklyn.util.internal.ConfigKeySelfExtracting;

import com.google.common.base.Preconditions;

public class ConfigMap {

    private static final Logger LOG = LoggerFactory.getLogger(ConfigMap.class);

    private final AbstractEntity entity;

    /*
     * TODO An alternative implementation approach would be to have:
     *   setOwner(Entity o, Map<ConfigKey,Object> inheritedConfig=[:])
     * The idea is that the owner could in theory decide explicitly what in its config
     * would be shared.
     * I (Aled) am undecided as to whether that would be better...
     * 
     * (Alex) i lean toward the config key getting to make the decision
     */
    /**
     * Map of configuration information that is defined at start-up time for the entity. These
     * configuration parameters are shared and made accessible to the "owned children" of this
     * entity.
     */
    private final Map<ConfigKey<?>,Object> ownConfig = Collections.synchronizedMap(new LinkedHashMap<ConfigKey<?>, Object>());
    private final Map<ConfigKey<?>,Object> inheritedConfig = Collections.synchronizedMap(new LinkedHashMap<ConfigKey<?>, Object>());

    public ConfigMap(AbstractEntity entity) {
        this.entity = Preconditions.checkNotNull(entity, "entity must be specified");
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
    
    //don't use groovy defaults for defaultValue as that doesn't implement the contract; we need the above
    public <T> T getConfig(ConfigKey<T> key, T defaultValue) {
        // FIXME What about inherited task in config?!
        //              alex says: think that should work, no?
        // FIXME What if someone calls getConfig on a task, before setting parent app?
        //              alex says: not supported (throw exception, or return the task)
        
        // In case this entity class has overridden the given key (e.g. to set default), then retrieve this entity's key
        // TODO If ask for a config value that's not in our configKeys, should we really continue with rest of method and return key.getDefaultValue?
        //      e.g. SshBasedJavaAppSetup calls setAttribute(JMX_USER), which calls getConfig(JMX_USER)
        //           but that example doesn't have a default...
        ConfigKey<T> ownKey = elvis(entity.getConfigKey(key.getName()), key);
        
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

    public Map<ConfigKey<?>,Object> getAllConfig() {
        // FIXME What about task-based config?!
        Map<ConfigKey<?>,Object> result = new LinkedHashMap<ConfigKey<?>,Object>(inheritedConfig.size()+ownConfig.size());
        result.putAll(inheritedConfig);
        result.putAll(ownConfig);
        return Collections.unmodifiableMap(result);
    }

    public <T> T setConfig(ConfigKey<T> key, T v) {
        Object val;
        if ((v instanceof Future) || (v instanceof Closure)) {
            //no coercion for these (yet)
            val = v;
        } else {
            try {
                val = TypeCoercions.coerce(v, key.getType());
            } catch (Exception e) {
                throw new IllegalArgumentException("Cannot coerce or set "+v+" to "+key, e);
            }
        }
        T oldVal = (T) ownConfig.put(key, val);
        entity.refreshInheritedConfigOfChildren();

        return oldVal;
    }
    
    public void setInheritedConfig(Map<ConfigKey<?>, ? extends Object> vals) {
        inheritedConfig.putAll(vals);
    }
    
    public void clearInheritedConfig() {
        inheritedConfig.clear();
    }
}
