package brooklyn.entity.rebind;

import brooklyn.mementos.Memento;

public interface Rebindable<T extends Memento> {

    public RebindSupport<T> getRebindSupport();
    
}
