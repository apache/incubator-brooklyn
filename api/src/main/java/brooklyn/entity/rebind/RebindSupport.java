package brooklyn.entity.rebind;

import brooklyn.mementos.Memento;

/**
 * Supporter instance for behaviour related to rebinding a given entity.
 *  
 * @author aled
 */
public interface RebindSupport<T extends Memento> {

    /**
     * Creates a memento representing this entity's current state. This is useful for when restarting brooklyn.
     */
    public T getMemento();

    /**
     * Reconstructs this entity, given a memento of its state. Sets the internal state 
     * (including id and config keys), and sets the parent/children/locations of this entity.
     * 
     * Implementations should be very careful to not invoke or inspect these other entities/locations,
     * as they may also be being reconstructed at this time.
     * 
     * Called before rebind.
     */
    public void reconstruct(RebindContext rebindContext, T memento);

    /**
     * Rebinds a re-constructed entity, to restore its state and connectivity, as it was when 
     * the memento was created.
     * 
     * Called after reconstruct.
     */
    public void rebind(RebindContext rebindContext, T memento);
    
    /**
     * Called after this entity (and all other entities/locations in the RebindContext) have completed 
     * their rebind.
     * 
     * TODO Relationship with AbstractEntity.onManagementBecomingMaster
     */
    public void managed();
}
