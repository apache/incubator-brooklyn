package org.overpaas.entities;

import java.lang.reflect.Method
import java.util.Collection
import java.util.List

public class EntityClass implements Serializable {
    final String name;
    final Collection<Sensor> sensors;
    final Collection<Effector> effectors;

    EntityClass(String name, Collection<Sensor> sensors, Collection<Effector> effectors) {
        this.name = name;
        this.sensors = sensors;
        this.effectors = effectors;
    }

    // TODO Could have constructor that takes a Class<? extends Entity>, that introspects 
    // the fields/methods for appropriate annotations
        
    // TODO maybe these? discuss/delete
    //Collection<EntityType> getSuperTypes();
    //boolean isInstance(Entity);
}

// Modeled on concepts in MBeanOperationInfo
public class Effector implements Serializable {
    final String name;
    final String returnType;
    final List<ParameterType> parameters;
    final String description;

    Effector(Method m) {
        name = m.getName();
        returnType = m.getReturnType().getName();
        parameters = []
        m.getParameterTypes().each { parameters.add new ParameterType("", it, "") }
        description = "";
    }
    
    Effector(String name, String returnType, List<ParameterType> parameters, String description) {
        this.name = name;
        this.returnType = returnType;
        this.parameters = parameters;
        this.description = description;
    }
}

// Modeled on concepts in MBeanParameterInfo
public class ParameterType implements Serializable {
    String name;
    String type;
    String description;
    
    ParameterType(String name, String type, String description) {
        this.name = name;
        this.type = type;
        this.description = description;
    }
}
