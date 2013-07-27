package brooklyn.policy.basic;

import static brooklyn.util.GroovyJavaMethods.elvis;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.config.ConfigKey.HasConfigKey;
import brooklyn.entity.basic.ConfigMapViewWithStringKeys;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.event.basic.StructuredConfigKey;
import brooklyn.management.ExecutionContext;
import brooklyn.util.flags.TypeCoercions;
import brooklyn.util.internal.ConfigKeySelfExtracting;
import brooklyn.util.task.DeferredSupplier;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.Maps;

@SuppressWarnings("deprecation")
public class PolicyConfigMap implements brooklyn.config.ConfigMap {

    private static final Logger LOG = LoggerFactory.getLogger(PolicyConfigMap.class);

    /** policy against which config resolution / task execution will occur */
    private final AbstractPolicy policy;

    private final ConfigMapViewWithStringKeys mapViewWithStringKeys = new ConfigMapViewWithStringKeys(this);

    /*
     * TODO An alternative implementation approach would be to have:
     *   setParent(Entity o, Map<ConfigKey,Object> inheritedConfig=[:])
     * The idea is that the parent could in theory decide explicitly what in its config
     * would be shared.
     * I (Aled) am undecided as to whether that would be better...
     * 
     * (Alex) i lean toward the config key getting to make the decision
     */
    /**
     * Map of configuration information that is defined at start-up time for the entity. These
     * configuration parameters are shared and made accessible to the "children" of this
     * entity.
     */
    private final Map<ConfigKey<?>,Object> ownConfig = Collections.synchronizedMap(new LinkedHashMap<ConfigKey<?>, Object>());

    public PolicyConfigMap(AbstractPolicy policy) {
        this.policy = Preconditions.checkNotNull(policy, "policy must be specified");
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
        ConfigKey<T> ownKey = policy!=null ? (ConfigKey<T>)elvis(policy.getPolicyType().getConfigKey(key.getName()), key) : key;
        
        // Don't use groovy truth: if the set value is e.g. 0, then would ignore set value and return default!
        if (ownKey instanceof ConfigKeySelfExtracting) {
            if (((ConfigKeySelfExtracting<T>)ownKey).isSet(ownConfig)) {
                // FIXME Should we support config from futures? How to get execution context before setEntity?
                EntityLocal entity = policy.entity;
                ExecutionContext exec = (entity != null) ? ((EntityInternal)entity).getExecutionContext() : null;
                return ((ConfigKeySelfExtracting<T>)ownKey).extractValue(ownConfig, exec);
            }
        } else {
            LOG.warn("Config key {} of {} is not a ConfigKeySelfExtracting; cannot retrieve value; returning default", ownKey, this);
        }
        return TypeCoercions.coerce((defaultValue != null) ? defaultValue : ownKey.getDefaultValue(), key.getTypeToken());
    }
    
    @Override
    public Object getRawConfig(ConfigKey<?> key) {
        if (ownConfig.containsKey(key)) return ownConfig.get(key);
        return null;
    }
    
    /** returns the config of this policy */
    public Map<ConfigKey<?>,Object> getAllConfig() {
        // Don't use ImmutableMap because valide for values to be null
        return Collections.unmodifiableMap(Maps.newLinkedHashMap(ownConfig));
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
        return oldVal;
    }
    
    @Override
    public PolicyConfigMap submap(Predicate<ConfigKey<?>> filter) {
        PolicyConfigMap m = new PolicyConfigMap(policy);
        for (Map.Entry<ConfigKey<?>,Object> entry: ownConfig.entrySet())
            if (filter.apply(entry.getKey()))
                m.ownConfig.put(entry.getKey(), entry.getValue());
        return m;
    }

    @Override
    public String toString() {
        return super.toString()+"[own="+Entities.sanitize(ownConfig)+"]";
    }
    
    public Map<String,Object> asMapWithStringKeys() {
        return mapViewWithStringKeys;
    }

    @Override
    public int size() {
        return ownConfig.size();
    }

    @Override
    public boolean isEmpty() {
        return ownConfig.isEmpty();
    }
}
