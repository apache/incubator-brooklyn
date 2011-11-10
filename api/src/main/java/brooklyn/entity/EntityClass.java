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
	//(or is this even necessary; info is on the EntityLocal, and that may be more up to date than this?) 
    
    // TODO maybe these? discuss/delete
    //Collection<EntityType> getSuperTypes();
    //boolean isInstance(Entity);
    
    //FIXME Alex: suggest we allow these, but always defer to the java type (i.e. use BasicEntitySummary, delete ExplicitEntitySummary) 

    String getName();
    
    Collection<ConfigKey<?>> getConfigKeys();
    Collection<Sensor<?>> getSensors();
    Collection<Effector<?>> getEffectors();
    

}
