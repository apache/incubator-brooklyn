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

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Collection;
import java.util.Map;

import brooklyn.basic.BrooklynObject;
import brooklyn.catalog.CatalogItem;
import brooklyn.entity.Entity;
import brooklyn.entity.Feed;
import brooklyn.location.Location;
import brooklyn.mementos.BrooklynMementoPersister.LookupContext;
import brooklyn.policy.Enricher;
import brooklyn.policy.Policy;
import brooklyn.util.collections.MutableMap;

import com.google.common.collect.Maps;

public class RebindContextImpl implements RebindContext {

    private final Map<String, Entity> entities = Maps.newLinkedHashMap();
    private final Map<String, Location> locations = Maps.newLinkedHashMap();
    private final Map<String, Policy> policies = Maps.newLinkedHashMap();
    private final Map<String, Enricher> enrichers = Maps.newLinkedHashMap();
    private final Map<String, Feed> feeds = Maps.newLinkedHashMap();
    private final Map<String, CatalogItem<?, ?>> catalogItems = Maps.newLinkedHashMap();
    
    private final ClassLoader classLoader;
    private final RebindExceptionHandler exceptionHandler;
    private LookupContext lookupContext;
    
    private boolean allAreReadOnly = false;
    
    public RebindContextImpl(RebindExceptionHandler exceptionHandler, ClassLoader classLoader) {
        this.exceptionHandler = checkNotNull(exceptionHandler, "exceptionHandler");
        this.classLoader = checkNotNull(classLoader, "classLoader");
    }

    public void registerEntity(String id, Entity entity) {
        entities.put(id, entity);
    }
    
    public void registerLocation(String id, Location location) {
        locations.put(id, location);
    }
    
    public void registerPolicy(String id, Policy policy) {
        policies.put(id, policy);
    }
    
    public void registerEnricher(String id, Enricher enricher) {
        enrichers.put(id, enricher);
    }
    
    public void registerFeed(String id, Feed feed) {
        feeds.put(id, feed);
    }
    
    public void registerCatalogItem(String id, CatalogItem<?, ?> catalogItem) {
        catalogItems.put(id, catalogItem);
    }
    
    public void unregisterPolicy(Policy policy) {
        policies.remove(policy.getId());
    }

    public void unregisterEnricher(Enricher enricher) {
        enrichers.remove(enricher.getId());
    }

    public void unregisterFeed(Feed feed) {
        feeds.remove(feed.getId());
    }

    public void unregisterCatalogItem(CatalogItem<?,?> item) {
        catalogItems.remove(item.getId());
    }

    public Entity getEntity(String id) {
        return entities.get(id);
    }

    public Location getLocation(String id) {
        return locations.get(id);
    }
    
    public Policy getPolicy(String id) {
        return policies.get(id);
    }
    
    public Enricher getEnricher(String id) {
        return enrichers.get(id);
    }

    public CatalogItem<?, ?> getCatalogItem(String id) {
        return catalogItems.get(id);
    }

    public Feed getFeed(String id) {
        return feeds.get(id);
    }
    
    public Class<?> loadClass(String className) throws ClassNotFoundException {
        return classLoader.loadClass(className);
    }

    public RebindExceptionHandler getExceptionHandler() {
        return exceptionHandler;
    }

    public Collection<Location> getLocations() {
        return locations.values();
    }
    
    public Collection<Entity> getEntities() {
        return entities.values();
    }
    
    public Collection<Policy> getPolicies() {
        return policies.values();
    }

    public Collection<Enricher> getEnrichers() {
        return enrichers.values();
    }
    
    public Collection<Feed> getFeeds() {
        return feeds.values();
    }

    public Collection<CatalogItem<?, ?>> getCatalogItems() {
        return catalogItems.values();
    }
    
    @Override
    public Map<String,BrooklynObject> getAllBrooklynObjects() {
        MutableMap<String,BrooklynObject> result = MutableMap.of();
        result.putAll(locations);
        result.putAll(entities);
        result.putAll(policies);
        result.putAll(enrichers);
        result.putAll(feeds);
        result.putAll(catalogItems);
        return result.asUnmodifiable();
    }

    public void setAllReadOnly() {
        allAreReadOnly = true;
    }
    
    public boolean isReadOnly(BrooklynObject item) {
        return allAreReadOnly;
    }

    public void setLookupContext(LookupContext lookupContext) {
        this.lookupContext = lookupContext;
    }
    
    @Override
    public LookupContext lookup() {
        return lookupContext;
    }
    
}
