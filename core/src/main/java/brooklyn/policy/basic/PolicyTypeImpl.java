package brooklyn.policy.basic;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.config.ConfigKey.HasConfigKey;
import brooklyn.policy.Policy;
import brooklyn.policy.PolicyType;

import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;

/**
 * This is the actual type of a policy instance at runtime.
 */
public class PolicyTypeImpl implements PolicyType {
    private static final long serialVersionUID = -662979234559595903L;

    private static final Logger LOG = LoggerFactory.getLogger(PolicyTypeImpl.class);

    private final String name;
    private final Map<String, ConfigKey<?>> configKeys;
    private final Set<ConfigKey<?>> configKeysSet;

    public PolicyTypeImpl(AbstractPolicy policy) {
        this(policy.getClass(), policy);
    }
    protected PolicyTypeImpl(Class<? extends Policy> clazz) {
        this(clazz, null);
    }
    private PolicyTypeImpl(Class<? extends Policy> clazz, AbstractPolicy policy) {
        name = clazz.getCanonicalName();
        configKeys = Collections.unmodifiableMap(findConfigKeys(clazz, policy));
        configKeysSet = ImmutableSet.copyOf(this.configKeys.values());
        if (LOG.isTraceEnabled())
            LOG.trace("Policy {} config keys: {}", name, Joiner.on(", ").join(configKeys.keySet()));
    }
    
    PolicyTypeImpl(String name, Map<String, ConfigKey<?>> configKeys) {
        this.name = name;
        this.configKeys = ImmutableMap.copyOf(configKeys);
        this.configKeysSet = ImmutableSet.copyOf(this.configKeys.values());
    }

    @Override
    public String getName() {
        return name;
    }
    
    @Override
    public Set<ConfigKey<?>> getConfigKeys() {
        return configKeysSet;
    }
    
    @Override
    public ConfigKey<?> getConfigKey(String name) {
        return configKeys.get(name);
    }
    
    @Override
    public int hashCode() {
        return Objects.hashCode(name, configKeys);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof PolicyType)) return false;
        PolicyType o = (PolicyType) obj;
        
        return Objects.equal(name, o.getName()) && Objects.equal(getConfigKeys(), o.getConfigKeys());
    }
    
    @Override
    public String toString() {
        return Objects.toStringHelper(name)
                .add("configKeys", configKeys)
                .toString();
    }
    
    /**
     * Finds the config keys defined on the entity's class, statics and optionally any non-static (discouraged).
     */
    // TODO Remove duplication from EntityDynamicType
    protected static Map<String,ConfigKey<?>> findConfigKeys(Class<? extends Policy> clazz, Policy optionalPolicy) {
        try {
            Map<String,ConfigKey<?>> result = Maps.newLinkedHashMap();
            Map<String,Field> configFields = Maps.newLinkedHashMap();
            for (Field f : clazz.getFields()) {
                boolean isConfigKey = ConfigKey.class.isAssignableFrom(f.getType());
                if (!isConfigKey) {
                    if (!HasConfigKey.class.isAssignableFrom(f.getType())) {
                        // neither ConfigKey nor HasConfigKey
                        continue;
                    }
                }
                if (!Modifier.isStatic(f.getModifiers())) {
                    // require it to be static or we have an instance
                    LOG.warn("Discouraged use of non-static config key "+f+" defined in " + (optionalPolicy!=null ? optionalPolicy : clazz));
                    if (optionalPolicy==null) continue;
                }
                ConfigKey<?> k = isConfigKey ? (ConfigKey<?>) f.get(optionalPolicy) : 
                    ((HasConfigKey<?>)f.get(optionalPolicy)).getConfigKey();

                Field alternativeField = configFields.get(k.getName());
                // Allow overriding config keys (e.g. to set default values) when there is an assignable-from relationship between classes
                Field definitiveField = alternativeField != null ? inferSubbestField(alternativeField, f) : f;
                boolean skip = false;
                if (definitiveField != f) {
                    // If they refer to the _same_ instance, just keep the one we already have
                    if (alternativeField.get(optionalPolicy) == f.get(optionalPolicy)) skip = true;
                }
                if (skip) {
                    //nothing
                } else if (definitiveField == f) {
                    result.put(k.getName(), k);
                    configFields.put(k.getName(), f);
                } else if (definitiveField != null) {
                    if (LOG.isDebugEnabled()) LOG.debug("multiple definitions for config key {} on {}; preferring that in sub-class: {} to {}", new Object[] {
                            k.getName(), optionalPolicy!=null ? optionalPolicy : clazz, alternativeField, f});
                } else if (definitiveField == null) {
                    LOG.warn("multiple definitions for config key {} on {}; preferring {} to {}", new Object[] {
                            k.getName(), optionalPolicy!=null ? optionalPolicy : clazz, alternativeField, f});
                }
            }
            
            return result;
        } catch (IllegalAccessException e) {
            throw Throwables.propagate(e);
        }
    }
    
    /**
     * Gets the field that is in the sub-class; or null if one field does not come from a sub-class of the other field's class
     */
    // TODO Remove duplication from EntityDynamicType
    private static Field inferSubbestField(Field f1, Field f2) {
        Class<?> c1 = f1.getDeclaringClass();
        Class<?> c2 = f2.getDeclaringClass();
        boolean isSuper1 = c1.isAssignableFrom(c2);
        boolean isSuper2 = c2.isAssignableFrom(c1);
        return (isSuper1) ? (isSuper2 ? null : f2) : (isSuper2 ? f1 : null);
    }
    
}
