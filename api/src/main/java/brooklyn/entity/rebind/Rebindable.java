package brooklyn.entity.rebind;

import brooklyn.mementos.Memento;

/**
 * Indicates that this can be recreated, e.g. after a brooklyn restart, and by
 * using a {@link Memento} it can repopulate the brooklyn objects. The purpose
 * of the rebind is to reconstruct and reconnect the brooklyn objects, including
 * binding them to external resources.
 */
public interface Rebindable<T extends Memento> {

    public RebindSupport<T> getRebindSupport();
    
}
