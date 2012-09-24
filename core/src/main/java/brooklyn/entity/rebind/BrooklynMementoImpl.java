package brooklyn.entity.rebind;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import brooklyn.entity.Application;
import brooklyn.entity.Entity;
import brooklyn.location.Location;
import brooklyn.management.ManagementContext;
import brooklyn.mementos.BrooklynMemento;
import brooklyn.mementos.EntityMemento;
import brooklyn.mementos.LocationMemento;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class BrooklynMementoImpl implements BrooklynMemento, Serializable {

    private static final long serialVersionUID = -5848083830410137654L;
    
    private final List<String> applicationIds = Lists.newArrayList();
    private final Map<String, EntityMemento> entities = Maps.newLinkedHashMap();
    private final Map<String, LocationMemento> locations = Maps.newLinkedHashMap();
    
    public BrooklynMementoImpl(ManagementContext managementContext, Collection<Application> applications) {
        for (Application app : applications) {
            applicationIds.add(app.getId());
        }
        for (Entity entity : managementContext.getEntities()) {
            entities.put(entity.getId(), ((Rebindable)entity).getRebindSupport().getMemento());
            
            for (Location location : entity.getLocations()) {
                if (!locations.containsKey(location.getId())) {
                    locations.put(location.getId(), ((RebindableLocation)location).getRebindSupport().getMemento());
                }
            }
        }
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
}
