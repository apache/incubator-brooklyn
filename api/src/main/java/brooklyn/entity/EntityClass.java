package brooklyn.entity;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;

import brooklyn.event.Sensor;

public class EntityClass implements Serializable {
    final String name;
    final Collection<Sensor> sensors;
    final Collection<Effector> effectors;

    EntityClass(String name, Collection<Sensor> sensors, Collection<Effector> effectors) {
        this.name = name;
        this.sensors = sensors;
        this.effectors = effectors;
    }

    // TODO Constructor that takes a Class<? extends Entity>, that introspects 
    // the fields/methods for appropriate annotations to infer sensors/effectors
    // find all fields here (or in delegates?) which are Sensor objects (statics only? statics and fields? include entity properties map?)
    // find all fields here (or in delegates) annotated with @Effector ?
    
        
    // TODO maybe these? discuss/delete
    //Collection<EntityType> getSuperTypes();
    //boolean isInstance(Entity);
}
