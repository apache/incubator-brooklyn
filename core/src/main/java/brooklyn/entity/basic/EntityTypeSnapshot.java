package brooklyn.entity.basic;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Effector;
import brooklyn.entity.EntityType;
import brooklyn.entity.ParameterType;
import brooklyn.event.Sensor;

import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

public class EntityTypeSnapshot implements EntityType {
    private static final long serialVersionUID = 4670930188951106009L;
    
    private final String name;
    private final String simpleName;
    private final Map<String, ConfigKey<?>> configKeys;
    private final Map<String, Sensor<?>> sensors;
    private final Set<Effector<?>> effectors;
    private final Set<ConfigKey<?>> configKeysSet;
    private final Set<Sensor<?>> sensorsSet;

    EntityTypeSnapshot(String name, String simpleName, Map<String, ConfigKey<?>> configKeys, Map<String, Sensor<?>> sensors, Collection<Effector<?>> effectors) {
        this.name = name;
        this.simpleName = simpleName;
        this.configKeys = ImmutableMap.copyOf(configKeys);
        this.sensors = ImmutableMap.copyOf(sensors);
        this.effectors = ImmutableSet.copyOf(effectors);
        this.configKeysSet = ImmutableSet.copyOf(this.configKeys.values());
        this.sensorsSet = ImmutableSet.copyOf(this.sensors.values());
    }

    @Override
    public String getName() {
        return name;
    }
    
    @Override
    public String getSimpleName() {
        return simpleName;
    }
    
    @Override
    public Set<ConfigKey<?>> getConfigKeys() {
        return configKeysSet;
    }
    
    @Override
    public Set<Sensor<?>> getSensors() {
        return sensorsSet;
    }
    
    @Override
    public Set<Effector<?>> getEffectors() {
        return effectors;
    }
    
    @Override
    public Effector<?> getEffector(String name, Class<?>... parameterTypes) {
        // TODO Could index for more efficient lookup (e.g. by name in a MultiMap, or using name+parameterTypes as a key)
        // TODO Looks for exact match; could go for what would be valid to call (i.e. if parameterType is sub-class of ParameterType.getParameterClass then ok)
        // TODO Could take into account ParameterType.getDefaultValue() for what can be omitted
        
        effectorLoop : for (Effector<?> contender : effectors) {
            if (name.equals(contender.getName())) {
                List<ParameterType<?>> contenderParameters = contender.getParameters();
                if (parameterTypes.length == contenderParameters.size()) {
                    for (int i = 0; i < parameterTypes.length; i++) {
                        if (parameterTypes[i] != contenderParameters.get(i).getParameterClass()) {
                            continue effectorLoop;
                        }
                    }
                    return contender;
                }
            }
        }
        throw new NoSuchElementException("No matching effector "+name+"("+Joiner.on(", ").join(parameterTypes)+") on entity "+getName());
    }

    
    @Override
    public ConfigKey<?> getConfigKey(String name) {
        return configKeys.get(name);
    }
    
    @Override
    public Sensor<?> getSensor(String name) {
        return sensors.get(name);
    }

    @Override
    public boolean hasSensor(String name) {
        return sensors.containsKey(name);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(name, configKeys, sensors, effectors);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof EntityTypeSnapshot)) return false;
        EntityTypeSnapshot o = (EntityTypeSnapshot) obj;
        
        return Objects.equal(name, o.name) && Objects.equal(configKeys, o.configKeys) &&
                Objects.equal(sensors, o.sensors) && Objects.equal(effectors, o.effectors);
    }
    
    @Override
    public String toString() {
        return Objects.toStringHelper(name)
                .add("configKeys", configKeys)
                .add("sensors", sensors)
                .add("effectors", effectors)
                .toString();
    }
}
