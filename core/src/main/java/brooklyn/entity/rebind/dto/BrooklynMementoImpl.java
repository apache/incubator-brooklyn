package brooklyn.entity.rebind.dto;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import brooklyn.mementos.BrooklynMemento;
import brooklyn.mementos.EntityMemento;
import brooklyn.mementos.LocationMemento;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class BrooklynMementoImpl implements BrooklynMemento, Serializable {

    // FIXME There are race conditions for constructing the memento while entities are modifying their parent-child relationships
    
    private static final long serialVersionUID = -5848083830410137654L;
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        protected List<String> applicationIds = Lists.newArrayList();
        protected List<String> topLevelLocationIds = Lists.newArrayList();
        protected Map<String, EntityMemento> entities = Maps.newLinkedHashMap();
        protected Map<String, LocationMemento> locations = Maps.newLinkedHashMap();
        
        public Builder applicationIds(List<String> vals) {
            applicationIds.addAll(vals); return this;
        }
        public Builder topLevelLocationIds(List<String> vals) {
            topLevelLocationIds.addAll(vals); return this;
        }
        public Builder entities(Map<String, EntityMemento> vals) {
            entities.putAll(vals); return this;
        }
        public Builder locations(Map<String, LocationMemento> vals) {
            locations.putAll(vals); return this;
        }
        public BrooklynMemento build() {
            return new BrooklynMementoImpl(this);
        }
    }

    private List<String> applicationIds;
    private List<String> topLevelLocationIds;
    private Map<String, EntityMemento> entities;
    private Map<String, LocationMemento> locations;
    
    private BrooklynMementoImpl(Builder builder) {
        applicationIds = Collections.unmodifiableList(builder.applicationIds);
        topLevelLocationIds = Collections.unmodifiableList(builder.topLevelLocationIds);
        entities = Collections.unmodifiableMap(builder.entities);
        locations = Collections.unmodifiableMap(builder.locations);
    }

    @Override
    public EntityMemento getEntityMemento(String id) {
        return entities.get(id);
    }

    @Override
    public LocationMemento getLocationMemento(String id) {
        return locations.get(id);
    }
    
    @Override
    public Collection<String> getApplicationIds() {
        return ImmutableList.copyOf(applicationIds);
    }

    @Override
    public Collection<String> getEntityIds() {
        return Collections.unmodifiableSet(entities.keySet());
    }
    
    @Override
    public Collection<String> getLocationIds() {
        return Collections.unmodifiableSet(locations.keySet());
    }
    
    @Override
    public Collection<String> getTopLevelLocationIds() {
        return Collections.unmodifiableList(topLevelLocationIds);
    }

    @Override
    public Map<String, EntityMemento> getEntityMementos() {
        return Collections.unmodifiableMap(entities);
    }
    @Override
    public Map<String, LocationMemento> getLocationMementos() {
        return Collections.unmodifiableMap(locations);
    }
}
