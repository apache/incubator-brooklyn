package brooklyn.entity.rebind;

import brooklyn.mementos.EntityMemento;

public interface Rebindable {

    public RebindSupport<EntityMemento> getRebindSupport();
    
}
