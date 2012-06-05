package brooklyn.entity.basic;

import java.util.Collection;

import brooklyn.entity.ConfigKey;
import brooklyn.entity.Effector;
import brooklyn.entity.EntityClass;
import brooklyn.event.Sensor;

import com.google.common.base.Objects;

public class BasicEntityClass implements EntityClass {
    private static final long serialVersionUID = 4670930188951106009L;
    
    private String name;
    private Collection<ConfigKey<?>> configKeys;
    private Collection<Sensor<?>> sensors;
    private Collection<Effector<?>> effectors;

    @Override
    public int hashCode() {
        return Objects.hashCode(name, configKeys, sensors, effectors);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof BasicEntityClass)) return false;
        BasicEntityClass o = (BasicEntityClass) obj;
        
        return Objects.equal(name, o.name) && Objects.equal(configKeys, o.configKeys) &&
                Objects.equal(sensors, o.sensors) && Objects.equal(effectors, o.effectors);
    }
    
    @SuppressWarnings("unused")
    private BasicEntityClass() { /* for gson */ }

    BasicEntityClass(String name, Collection<ConfigKey<?>> configKeys, Collection<Sensor<?>> sensors, Collection<Effector<?>> effectors) {
        this.name = name;
        this.configKeys = configKeys;
        this.sensors = sensors;
        this.effectors = effectors;
    }

    public String getName() {
        return name;
    }
    
    public Collection<ConfigKey<?>> getConfigKeys() {
        return configKeys;
    }
    
    public Collection<Sensor<?>> getSensors() {
        return sensors;
    }
    
    public Collection<Effector<?>> getEffectors() {
        return effectors;
    }

}
