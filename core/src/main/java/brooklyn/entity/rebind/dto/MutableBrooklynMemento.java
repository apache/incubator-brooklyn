/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package brooklyn.entity.rebind.dto;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import org.codehaus.jackson.annotate.JsonAutoDetect;
import org.codehaus.jackson.annotate.JsonAutoDetect.Visibility;

import brooklyn.mementos.BrooklynMemento;
import brooklyn.mementos.EnricherMemento;
import brooklyn.mementos.EntityMemento;
import brooklyn.mementos.LocationMemento;
import brooklyn.mementos.PolicyMemento;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;


/**
 * @deprecated since 0.7.0; this code will move to src/tests/java
 */
@JsonAutoDetect(fieldVisibility=Visibility.ANY, getterVisibility=Visibility.NONE)
public class MutableBrooklynMemento implements BrooklynMemento {

    // TODO Is this class pulling its weight? Do we really need it?

    private static final long serialVersionUID = -442895028005849060L;
    
    private final Collection<String> applicationIds = Sets.newLinkedHashSet();
    private final Collection<String> topLevelLocationIds = Sets.newLinkedHashSet();
    private final Map<String, EntityMemento> entities = Maps.newLinkedHashMap();
    private final Map<String, LocationMemento> locations = Maps.newLinkedHashMap();
    private final Map<String, PolicyMemento> policies = Maps.newLinkedHashMap();
    private final Map<String, EnricherMemento> enrichers = Maps.newLinkedHashMap();

    public MutableBrooklynMemento() {
    }
    
    public MutableBrooklynMemento(BrooklynMemento memento) {
        reset(memento);
    }
    
    public void reset(BrooklynMemento memento) {
        applicationIds.addAll(memento.getApplicationIds());
        topLevelLocationIds.addAll(memento.getTopLevelLocationIds());
        for (String entityId : memento.getEntityIds()) {
            entities.put(entityId, checkNotNull(memento.getEntityMemento(entityId), entityId));
        }
        for (String locationId : memento.getLocationIds()) {
            locations.put(locationId, checkNotNull(memento.getLocationMemento(locationId), locationId));
        }
    }

    public void updateEntityMemento(EntityMemento memento) {
        updateEntityMementos(ImmutableSet.of(memento));
    }
    
    public void updateLocationMemento(LocationMemento memento) {
        updateLocationMementos(ImmutableSet.of(memento));
    }
    
    public void updatePolicyMemento(PolicyMemento memento) {
        updatePolicyMementos(ImmutableSet.of(memento));
    }
    
    public void updateEnricherMemento(EnricherMemento memento) {
        updateEnricherMementos(ImmutableSet.of(memento));
    }
    
    public void updateEntityMementos(Collection<EntityMemento> mementos) {
        for (EntityMemento memento : mementos) {
            entities.put(memento.getId(), memento);
            
            if (memento.isTopLevelApp()) {
                applicationIds.add(memento.getId());
            }
        }
    }
    
    public void updateLocationMementos(Collection<LocationMemento> mementos) {
        for (LocationMemento locationMemento : mementos) {
            locations.put(locationMemento.getId(), locationMemento);
            
            if (locationMemento.getParent() == null) {
                topLevelLocationIds.add(locationMemento.getId());
            }
        }
    }
    
    public void updatePolicyMementos(Collection<PolicyMemento> mementos) {
        for (PolicyMemento memento : mementos) {
            policies.put(memento.getId(), memento);
        }
    }
    
    public void updateEnricherMementos(Collection<EnricherMemento> mementos) {
        for (EnricherMemento memento : mementos) {
            enrichers.put(memento.getId(), memento);
        }
    }
    
    /**
     * Removes the entities with the given ids.
     */
    public void removeEntities(Collection<String> ids) {
        entities.keySet().removeAll(ids);
        applicationIds.removeAll(ids);
    }
    
    /**
     * Removes the locations with the given ids.
     */
    public void removeLocations(Collection<String> ids) {
        locations.keySet().removeAll(ids);
        topLevelLocationIds.removeAll(ids);
    }

    /**
     * Removes the policies with the given ids.
     */
    public void removePolicies(Collection<String> ids) {
        policies.keySet().removeAll(ids);
    }

    /**
     * Removes the enrichers with the given ids.
     */
    public void removeEnrichers(Collection<String> ids) {
        enrichers.keySet().removeAll(ids);
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
    public PolicyMemento getPolicyMemento(String id) {
        return policies.get(id);
    }

    
    @Override
    public EnricherMemento getEnricherMemento(String id) {
        return enrichers.get(id);
    }
    
    @Override
    public Collection<String> getApplicationIds() {
        return ImmutableList.copyOf(applicationIds);
    }

    @Override
    public Collection<String> getEntityIds() {
        // TODO Return immutable copy? Synchronize while making copy?
        return Collections.unmodifiableSet(entities.keySet());
    }
    
    @Override
    public Collection<String> getLocationIds() {
        return Collections.unmodifiableSet(locations.keySet());
    }
    
    @Override
    public Collection<String> getPolicyIds() {
        return Collections.unmodifiableSet(policies.keySet());
    }
    
    @Override
    public Collection<String> getEnricherIds() {
        return Collections.unmodifiableSet(enrichers.keySet());
    }
    
    @Override
    public Collection<String> getTopLevelLocationIds() {
        return Collections.unmodifiableCollection(topLevelLocationIds);
    }

    @Override
    public Map<String, EntityMemento> getEntityMementos() {
        return ImmutableMap.copyOf(entities);
    }

    @Override
    public Map<String, LocationMemento> getLocationMementos() {
        return ImmutableMap.copyOf(locations);
    }

    @Override
    public Map<String, PolicyMemento> getPolicyMementos() {
        return ImmutableMap.copyOf(policies);
    }

    @Override
    public Map<String, EnricherMemento> getEnricherMementos() {
        return ImmutableMap.copyOf(enrichers);
    }
}
