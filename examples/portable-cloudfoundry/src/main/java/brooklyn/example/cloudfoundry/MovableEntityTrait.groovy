package brooklyn.example.cloudfoundry;

import brooklyn.entity.Effector
import brooklyn.entity.basic.Description
import brooklyn.entity.basic.MethodEffector
import brooklyn.entity.basic.NamedParameter

public interface MovableEntityTrait {

    Effector<String> MOVE = new MethodEffector<String>(MovableEntityTrait.&move);
            
    /** Effectively move the entity to the new location.
     * A new entity may be created (and the old destroyed) to effect this.
     * @param location the new location where the entity should running
     * @return the entity ID of the primary entity (after the move) in the specified location */
    @Description("Effectively move the entity to the new location.")
    public String move(
        @NamedParameter("location") @Description("The new location where the entity should be running") 
        String location);

}
