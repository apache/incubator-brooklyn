package brooklyn.entity.rebind;

import java.util.Collection;

import brooklyn.mementos.BrooklynMementoPersister.Delta;
import brooklyn.mementos.EnricherMemento;
import brooklyn.mementos.EntityMemento;
import brooklyn.mementos.LocationMemento;
import brooklyn.mementos.PolicyMemento;

import com.google.common.collect.Sets;

public class PersisterDeltaImpl implements Delta {
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private final PersisterDeltaImpl delta = new PersisterDeltaImpl();

        public Builder locations(Collection<? extends LocationMemento> vals) {
            delta.locations.addAll(vals);
            return this;
        }
        public Builder entities(Collection<? extends EntityMemento> vals) {
            delta.entities.addAll(vals);
            return this;
        }
        public Builder policies(Collection<? extends PolicyMemento> vals) {
            delta.policies.addAll(vals);
            return this;
        }
        public Builder enrichers(Collection<? extends EnricherMemento> vals) {
            delta.enrichers.addAll(vals);
            return this;
        }
        public Builder removedLocationIds(Collection<String> vals) {
            delta.removedLocationIds.addAll(vals);
            return this;
        }
        public Builder removedEntityIds(Collection<String> vals) {
            delta.removedEntityIds.addAll(vals);
            return this;
        }
        public Builder removedPolicyIds(Collection<String> vals) {
            delta.removedPolicyIds.addAll(vals);
            return this;
        }
        public Builder removedEnricherIds(Collection<String> vals) {
            delta.removedEnricherIds.addAll(vals);
            return this;
        }
        public Delta build() {
            return delta;
        }
    }
    
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
