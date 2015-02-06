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
package brooklyn.entity.rebind;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

import brooklyn.mementos.BrooklynMementoPersister.Delta;
import brooklyn.mementos.BrooklynMementoPersister.MutableDelta;
import brooklyn.mementos.CatalogItemMemento;
import brooklyn.mementos.EnricherMemento;
import brooklyn.mementos.EntityMemento;
import brooklyn.mementos.FeedMemento;
import brooklyn.mementos.LocationMemento;
import brooklyn.mementos.Memento;
import brooklyn.mementos.PolicyMemento;

import com.google.common.annotations.Beta;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;

public class PersisterDeltaImpl implements Delta, MutableDelta {
    
    // use multiset?
    
    Collection<LocationMemento> locations = Sets.newLinkedHashSet();
    Collection<EntityMemento> entities = Sets.newLinkedHashSet();
    Collection<PolicyMemento> policies = Sets.newLinkedHashSet();
    Collection<EnricherMemento> enrichers = Sets.newLinkedHashSet();
    Collection<FeedMemento> feeds = Sets.newLinkedHashSet();
    Collection<CatalogItemMemento> catalogItems = Sets.newLinkedHashSet();
    
    Collection<String> removedLocationIds = Sets.newLinkedHashSet();
    Collection<String> removedEntityIds = Sets.newLinkedHashSet();
    Collection<String> removedPolicyIds = Sets.newLinkedHashSet();
    Collection<String> removedEnricherIds = Sets.newLinkedHashSet();
    Collection <String> removedFeedIds = Sets.newLinkedHashSet();
    Collection<String> removedCatalogItemIds = Sets.newLinkedHashSet();

    @Override
    public Collection<LocationMemento> locations() {
        return Collections.unmodifiableCollection(locations);
    }

    @Override
    public Collection<EntityMemento> entities() {
        return Collections.unmodifiableCollection(entities);
    }

    @Override
    public Collection<PolicyMemento> policies() {
        return Collections.unmodifiableCollection(policies);
    }

    @Override
    public Collection<EnricherMemento> enrichers() {
        return Collections.unmodifiableCollection(enrichers);
    }
    
    @Override
    public Collection<FeedMemento> feeds() {
        return Collections.unmodifiableCollection(feeds);
    }

    @Override
    public Collection<CatalogItemMemento> catalogItems() {
        return Collections.unmodifiableCollection(catalogItems);
    }

    @Override
    public Collection<String> removedLocationIds() {
        return Collections.unmodifiableCollection(removedLocationIds);
    }

    @Override
    public Collection<String> removedEntityIds() {
        return Collections.unmodifiableCollection(removedEntityIds);
    }
    
    @Override
    public Collection<String> removedPolicyIds() {
        return Collections.unmodifiableCollection(removedPolicyIds);
    }
    
    @Override
    public Collection<String> removedEnricherIds() {
        return Collections.unmodifiableCollection(removedEnricherIds);
    }
    
    @Override
    public Collection<String> removedFeedIds() {
        return Collections.unmodifiableCollection(removedFeedIds);
    }

    @Override
    public Collection<String> removedCatalogItemIds() {
        return Collections.unmodifiableCollection(removedCatalogItemIds);
    }

    @Override
    public Collection<? extends Memento> getObjectsOfType(BrooklynObjectType type) {
        return Collections.unmodifiableCollection(getMutableObjectsOfType(type));
    }
    
    @SuppressWarnings("unchecked")
    @Beta
    private Collection<Memento> getMutableUncheckedObjectsOfType(BrooklynObjectType type) {
        return (Collection<Memento>)getMutableObjectsOfType(type);
    }
    private Collection<? extends Memento> getMutableObjectsOfType(BrooklynObjectType type) {
        switch (type) {
        case ENTITY: return entities;
        case LOCATION: return locations;
        case POLICY: return policies;
        case ENRICHER: return enrichers;
        case FEED: return feeds;
        case CATALOG_ITEM: return catalogItems;
        case UNKNOWN: 
        default:
            throw new IllegalArgumentException(type+" not supported");
        }
    }
    
    @Override
    public Collection<String> getRemovedIdsOfType(BrooklynObjectType type) {
        return Collections.unmodifiableCollection(getRemovedIdsOfTypeMutable(type));
    }
    
    private Collection<String> getRemovedIdsOfTypeMutable(BrooklynObjectType type) {
        switch (type) {
        case ENTITY: return removedEntityIds;
        case LOCATION: return removedLocationIds;
        case POLICY: return removedPolicyIds;
        case ENRICHER: return removedEnricherIds;
        case FEED: return removedFeedIds;
        case CATALOG_ITEM: return removedCatalogItemIds;
        case UNKNOWN: 
        default:
            throw new IllegalArgumentException(type+" not supported");
        }
    }

    public void add(BrooklynObjectType type, Memento memento) {
        getMutableUncheckedObjectsOfType(type).add(memento);
    }

    @Override
    public void addAll(BrooklynObjectType type, Iterable<? extends Memento> mementos) {
        Iterables.addAll(getMutableUncheckedObjectsOfType(type), mementos);
    }
    
    public void removed(BrooklynObjectType type, Set<String> removedIdsOfType) {
        getRemovedIdsOfTypeMutable(type).addAll(removedIdsOfType);    
    }

}
