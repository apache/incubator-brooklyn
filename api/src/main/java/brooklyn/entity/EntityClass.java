package brooklyn.entity;

import java.io.Serializable;
import java.util.Collection;

import brooklyn.event.Sensor;

/**
 * Represents the type of an {@link Entity}.
 * 
 * Analogous to {@link Class} for an instance.
 * 
 * TODO javadoc
 */
public interface EntityClass extends Serializable {
    
    //TODO call EntityType ?  instead of Class?  same for refs to SensorClass ?
    
    // TODO maybe these? discuss/delete
    //Collection<EntityType> getSuperTypes();
    //boolean isInstance(Entity);
    
    //FIXME Alex: suggest we allow these, but always defer to the java type (i.e. use BasicEntitySummary, delete ExplicitEntitySummary) 

    String getName();
    
    Collection<Sensor<?>> getSensors();
    
    Collection<Effector<?>> getEffectors();
}
