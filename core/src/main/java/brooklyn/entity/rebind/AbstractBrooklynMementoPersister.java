package brooklyn.entity.rebind;

import static com.google.common.base.Preconditions.checkNotNull;
import brooklyn.entity.rebind.dto.MutableBrooklynMemento;
import brooklyn.mementos.BrooklynMemento;
import brooklyn.mementos.BrooklynMementoPersister;

public abstract class AbstractBrooklynMementoPersister implements BrooklynMementoPersister {

    protected volatile MutableBrooklynMemento memento = new MutableBrooklynMemento();
    
    @Override
    public BrooklynMemento loadMemento() {
        // Trusting people not to cast+modify, because the in-memory persister wouldn't be used in production code
        return memento;
    }
    
    @Override
    public void checkpoint(BrooklynMemento newMemento) {
        memento.reset(checkNotNull(newMemento, "memento"));
    }

    @Override
    public void delta(Delta delta) {
        memento.removeEntities(delta.removedEntityIds());
        memento.removeLocations(delta.removedLocationIds());
        memento.updateEntityMementos(delta.entityMementos());
        memento.updateLocationMementos(delta.locationMementos());
    }
}
