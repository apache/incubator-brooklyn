package brooklyn.entity.rebind;

import brooklyn.mementos.LocationMemento;

public interface RebindableLocation {

    public RebindSupport<LocationMemento> getRebindSupport();
    
}
