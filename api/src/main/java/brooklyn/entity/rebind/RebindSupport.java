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
     * 
     * @see rebind(BrooklynMemento, String)
     */
    public T getMemento();

    /**
     * Reconstructs this entity, given a memento of its state. Sets only the internal state (including id and config keys),
     * but does not set up any wiring between entities or attempt to touch the outside world.
     * 
     * @see Entity.getMemento()
     * @see Entity.rebind()
     */
    public void reconstruct(T memento);

    /**
     * Rebinds a re-constructed entity, to restore its state and connectivity to as it was when the memento was created.
     * 
     * @see Entity.getMemento()
     * @see Entity.reconstruct()
     * @see BrooklynMemento.getEntityMemento(String)
     */
    public void rebind(RebindContext rebindContext, T memento);

}
