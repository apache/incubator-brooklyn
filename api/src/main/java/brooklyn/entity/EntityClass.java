package brooklyn.entity;

import java.io.Serializable;
import java.util.Collection;

import brooklyn.event.Sensor;

/**
 * Represents the type of an entity, analogous to java.lang.Class for an instance.
 *  
 * @author aled
 */
public class EntityClass implements Serializable {
    
    // TODO Constructor that takes a Class<? extends Entity>, that introspects 
    // the fields/methods for appropriate annotations to infer sensors/effectors
    // find all fields here (or in delegates?) which are Sensor objects (statics only? statics and fields? include entity properties map?)
    // find all fields here (or in delegates) annotated with @Effector ?
    
    // TODO maybe these? discuss/delete
    //Collection<EntityType> getSuperTypes();
    //boolean isInstance(Entity);
    
    private static final long serialVersionUID = 4670930188951106009L;
    
    private String name;
    private Collection<Sensor> sensors;
    private Collection<Effector> effectors;

    @SuppressWarnings("unused")
    private EntityClass() { /* for gson */ }

    EntityClass(String name, Collection<Sensor> sensors, Collection<Effector> effectors) {
        this.name = name;
        this.sensors = sensors;
        this.effectors = effectors;
    }

    public String getName() {
        return name;
    }
    
    public Collection<Sensor> getSensors() {
        return sensors;
    }
    
    public Collection<Effector> getEffectors() {
        return effectors;
    }
}
