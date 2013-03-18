package brooklyn.entity.rebind;

import brooklyn.mementos.BrooklynMementoPersister;
import brooklyn.mementos.Memento;

/**
 * Supporter instance for behaviour related to rebinding a given entity/location/policy.
 * 
 * For example, the brooklyn framework may call {@code entity.getRebindSupport().getMemento()}
 * and persist this using a {@link BrooklynMementoPersister}. Later (e.g. after a brooklyn
 * restart) a new entity instance may be created and populated by the framework calling 
 * {@code entity.getRebindSupport().reconstruct(rebindContext, memento)}.
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
}
