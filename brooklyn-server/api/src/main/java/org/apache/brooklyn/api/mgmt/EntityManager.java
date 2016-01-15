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
package org.apache.brooklyn.api.mgmt;

import java.util.Collection;
import java.util.Map;

import javax.annotation.Nullable;

import org.apache.brooklyn.api.entity.Application;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.entity.EntityTypeRegistry;
import org.apache.brooklyn.api.policy.Policy;
import org.apache.brooklyn.api.policy.PolicySpec;
import org.apache.brooklyn.api.sensor.Enricher;
import org.apache.brooklyn.api.sensor.EnricherSpec;

import com.google.common.base.Predicate;

/**
 * For managing and querying entities.
 */
public interface EntityManager {

    /**
     * Returns the type registry, used to identify the entity implementation when instantiating an
     * entity of a given type.
     * 
     * @see EntityManager.createEntity(EntitySpec)
     */
    EntityTypeRegistry getEntityTypeRegistry();
    
    /**
     * Creates a new entity. Management is started immediately (by this method).
     * 
     * @param spec
     * @return A proxy to the created entity (rather than the actual entity itself).
     */
    <T extends Entity> T createEntity(EntitySpec<T> spec);
    
    /**
     * Convenience (particularly for groovy code) to create an entity.
     * Equivalent to {@code createEntity(EntitySpec.create(type).configure(config))}
     * 
     * @see createEntity(EntitySpec)
     */
    <T extends Entity> T createEntity(Map<?,?> config, Class<T> type);

    /**
     * Creates a new policy (not managed; not associated with any entity).
     * 
     * @param spec
     */
    <T extends Policy> T createPolicy(PolicySpec<T> spec);

    /**
     * Creates a new enricher (not managed; not associated with any entity).
     * 
     * @param spec
     */
    <T extends Enricher> T createEnricher(EnricherSpec<T> spec);

    /**
     * All entities under control of this management plane
     */
    Collection<Entity> getEntities();

    /**
     * All entities managed as part of the given application
     */
    Collection<Entity> getEntitiesInApplication(Application application);

    /**
     * All entities under control of this management plane that match the given filter
     */
    Collection<Entity> findEntities(Predicate<? super Entity> filter);

    /**
     * All entities managed as part of the given application that match the given filter
     */
    Collection<Entity> findEntitiesInApplication(Application application, Predicate<? super Entity> filter);

    /**
     * Returns the entity with the given identifier (may be a full instance, or a proxy to one which is remote),
     * or null.
     */
    @Nullable Entity getEntity(String id);
    
    /** whether the entity is under management by this management context */
    boolean isManaged(Entity entity);

    /**
     * Begins management for the given entity and its children, recursively.
     *
     * depending on the implementation of the management context,
     * this might push it out to one or more remote management nodes.
     * Manage an entity.
     */
    // TODO manage and unmanage without arguments should be changed to take an explicit ManagementTransitionMode
    // (but that class is not currently in the API project)
    void manage(Entity e);
    
    /**
     * Causes the given entity and its children, recursively, to be removed from the management plane
     * (for instance because the entity is no longer relevant)
     */
    void unmanage(Entity e);
    
}
