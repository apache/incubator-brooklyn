package brooklyn.entity.basic;

import java.util.Collection;

import brooklyn.entity.Effector;
import brooklyn.event.Sensor;

// ENGR-1560 why not use BasicEntityClass?
public class ExplicitEntityClass {
    private static final long serialVersionUID = 4670930188951106009L;
    
    private String name;
    private Collection<Sensor<?>> sensors;
    private Collection<Effector<?, ?>> effectors;

    @SuppressWarnings("unused")
    private ExplicitEntityClass() { /* for gson */ }

    ExplicitEntityClass(String name, Collection<Sensor<?>> sensors, Collection<Effector<?, ?>> effectors) {
        this.name = name;
        this.sensors = sensors;
        this.effectors = effectors;
    }

    public String getName() {
        return name;
    }
    
    public Collection<Sensor<?>> getSensors() {
        return sensors;
    }
    
    public Collection<Effector<?, ?>> getEffectors() {
        return effectors;
    }

}
