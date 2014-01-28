package brooklyn.entity.rebind.persister;

import static com.google.common.base.Preconditions.checkNotNull;
import brooklyn.entity.rebind.dto.MutableBrooklynMemento;
import brooklyn.mementos.BrooklynMemento;
import brooklyn.mementos.BrooklynMementoPersister;

public abstract class AbstractBrooklynMementoPersister implements BrooklynMementoPersister {

    protected volatile MutableBrooklynMemento memento = new MutableBrooklynMemento();
    
    @Override
    public BrooklynMemento loadMemento(LookupContext lookupContext) {
        // Trusting people not to cast+modify, because the in-memory persister wouldn't be used in production code
        return memento;
    }
    
    @Override
    public void stop() {
        // no-op
    }
    
    @Override
    public void checkpoint(BrooklynMemento newMemento) {
        memento.reset(checkNotNull(newMemento, "memento"));
    }

    @Override
    public void delta(Delta delta) {
        memento.removeEntities(delta.removedEntityIds());
        memento.removeLocations(delta.removedLocationIds());
        memento.removePolicies(delta.removedPolicyIds());
        memento.updateEntityMementos(delta.entities());
        memento.updateLocationMementos(delta.locations());
        memento.updatePolicyMementos(delta.policies());
    }
}
