package brooklyn.entity.rebind.persister;

import static com.google.common.base.Preconditions.checkNotNull;
import brooklyn.entity.rebind.RebindExceptionHandler;
import brooklyn.entity.rebind.dto.BrooklynMementoManifestImpl;
import brooklyn.entity.rebind.dto.MutableBrooklynMemento;
import brooklyn.mementos.BrooklynMemento;
import brooklyn.mementos.BrooklynMementoManifest;
import brooklyn.mementos.BrooklynMementoPersister;
import brooklyn.mementos.EntityMemento;
import brooklyn.mementos.LocationMemento;
import brooklyn.mementos.PolicyMemento;

/**
 * @deprecated since 0.7.0 for production use {@link BrooklynMementoPersisterToMultiFile} instead; 
 *             this class will be merged with {@link BrooklynMementoPersisterInMemory} in test code.
 */
public abstract class AbstractBrooklynMementoPersister implements BrooklynMementoPersister {

    protected volatile MutableBrooklynMemento memento = new MutableBrooklynMemento();
    
    @Override
    public BrooklynMemento loadMemento(LookupContext lookupContext, RebindExceptionHandler exceptionHandler) {
        // Trusting people not to cast+modify, because the in-memory persister wouldn't be used in production code
        return memento;
    }
    
    @Override
    public BrooklynMementoManifest loadMementoManifest(RebindExceptionHandler exceptionHandler) {
        BrooklynMementoManifestImpl.Builder builder = BrooklynMementoManifestImpl.builder();
        for (EntityMemento entity : memento.getEntityMementos().values()) {
            builder.entity(entity.getId(), entity.getType());
        }
        for (LocationMemento entity : memento.getLocationMementos().values()) {
            builder.location(entity.getId(), entity.getType());
        }
        for (PolicyMemento entity : memento.getPolicyMementos().values()) {
            builder.policy(entity.getId(), entity.getType());
        }
        return builder.build();
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
