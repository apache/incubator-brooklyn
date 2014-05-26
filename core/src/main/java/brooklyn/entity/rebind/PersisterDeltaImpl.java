package brooklyn.entity.rebind;

import java.util.Collection;

import brooklyn.mementos.BrooklynMementoPersister.Delta;
import brooklyn.mementos.EnricherMemento;
import brooklyn.mementos.EntityMemento;
import brooklyn.mementos.LocationMemento;
import brooklyn.mementos.PolicyMemento;

import com.google.common.collect.Sets;

class PersisterDeltaImpl implements Delta {
    Collection<LocationMemento> locations = Sets.newLinkedHashSet();
    Collection<EntityMemento> entities = Sets.newLinkedHashSet();
    Collection<PolicyMemento> policies = Sets.newLinkedHashSet();
    Collection<EnricherMemento> enrichers = Sets.newLinkedHashSet();
    Collection <String> removedLocationIds = Sets.newLinkedHashSet();
    Collection <String> removedEntityIds = Sets.newLinkedHashSet();
    Collection <String> removedPolicyIds = Sets.newLinkedHashSet();
    Collection <String> removedEnricherIds = Sets.newLinkedHashSet();
    
    @Override
    public Collection<LocationMemento> locations() {
        return locations;
    }

    @Override
    public Collection<EntityMemento> entities() {
        return entities;
    }

    @Override
    public Collection<PolicyMemento> policies() {
        return policies;
    }

    @Override
    public Collection<EnricherMemento> enrichers() {
        return enrichers;
    }

    @Override
    public Collection<String> removedLocationIds() {
        return removedLocationIds;
    }

    @Override
    public Collection<String> removedEntityIds() {
        return removedEntityIds;
    }
    
    @Override
    public Collection<String> removedPolicyIds() {
        return removedPolicyIds;
    }
    
    @Override
    public Collection<String> removedEnricherIds() {
        return removedEnricherIds;
    }
}
