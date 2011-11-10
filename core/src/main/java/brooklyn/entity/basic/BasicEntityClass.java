package brooklyn.entity.basic;

import groovy.transform.EqualsAndHashCode;

import java.util.Collection;

import brooklyn.entity.ConfigKey;
import brooklyn.entity.Effector;
import brooklyn.entity.EntityClass;
import brooklyn.event.Sensor;

@EqualsAndHashCode(includeFields=true)
public class BasicEntityClass implements EntityClass {
    private static final long serialVersionUID = 4670930188951106009L;
    
    private String name;
    private Collection<ConfigKey<?>> configKeys;
    private Collection<Sensor<?>> sensors;
    private Collection<Effector<?>> effectors;

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
