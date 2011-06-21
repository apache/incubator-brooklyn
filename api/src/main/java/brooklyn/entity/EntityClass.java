package brooklyn.entity;

import java.io.Serializable;
import java.util.Collection;

import brooklyn.event.Sensor;

/**
 * Represents the type of an entity, analogous to java.lang.Class for an instance.
 *  
 * @author aled
 */
public interface EntityClass extends Serializable {
    
	//TODO call EntityType ?  instead of Class?  same for refs to SensorClass ?
	
    // TODO maybe these? discuss/delete
    //Collection<EntityType> getSuperTypes();
    //boolean isInstance(Entity);
	
	//FIXME Alex: suggest we allow these, but always defer to the java type (i.e. use BasicEntitySummary, delete ExplicitEntitySummary) 

    public String getName();
    
    public Collection<Sensor<?>> getSensors();
    
    public Collection<Effector> getEffectors();

}
