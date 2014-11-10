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
package brooklyn.entity.basic;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import brooklyn.basic.BrooklynObject.TagSupport;
import brooklyn.config.ConfigKey;
import brooklyn.config.ConfigKey.HasConfigKey;
import brooklyn.entity.Application;
import brooklyn.entity.Effector;
import brooklyn.entity.Entity;
import brooklyn.entity.EntityType;
import brooklyn.entity.Group;
import brooklyn.entity.basic.EntityInternal.FeedSupport;
import brooklyn.entity.proxying.EntityProxyImpl;
import brooklyn.entity.rebind.RebindSupport;
import brooklyn.event.AttributeSensor;
import brooklyn.location.Location;
import brooklyn.management.ExecutionContext;
import brooklyn.management.ManagementContext;
import brooklyn.management.internal.EntityManagementSupport;
import brooklyn.mementos.EntityMemento;
import brooklyn.policy.Enricher;
import brooklyn.policy.Policy;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.guava.Maybe;

import com.google.common.annotations.Beta;

/** 
 * Selected methods from {@link EntityInternal} and parents which are permitted
 * for entities being loaded in read-only mode, enforced by {@link EntityProxyImpl}.
 * <p>
 * Some of these methods do expose write capabilities, but such modifications are likely
 * to be temporary, discarded on next rebind. Callers must take care with any such invocations.
 * (The primary intent of this interface is to catch and prevent *most* such invocations!)
 */
@Beta
public interface EntityTransientCopyInternal {

    // from Entity
    
    String getId();
    long getCreationTime();
    String getDisplayName();
    @Nullable String getIconUrl();
    EntityType getEntityType();
    Application getApplication();
    String getApplicationId();
    Entity getParent();
    Collection<Entity> getChildren();
    Collection<Policy> getPolicies();
    Collection<Enricher> getEnrichers();
    Collection<Group> getGroups();
    Collection<Location> getLocations();
    <T> T getAttribute(AttributeSensor<T> sensor);
    <T> T getConfig(ConfigKey<T> key);
    <T> T getConfig(HasConfigKey<T> key);
    Maybe<Object> getConfigRaw(ConfigKey<?> key, boolean includeInherited);
    Maybe<Object> getConfigRaw(HasConfigKey<?> key, boolean includeInherited);
    @Deprecated Set<Object> getTags();
    @Deprecated boolean containsTag(@Nonnull Object tag);
    String getCatalogItemId();

    
    // from entity local
    
    @Deprecated <T> T getConfig(ConfigKey<T> key, T defaultValue);
    @Deprecated <T> T getConfig(HasConfigKey<T> key, T defaultValue);

    
    // from EntityInternal:
    
    EntityConfigMap getConfigMap();
    Map<ConfigKey<?>,Object> getAllConfig();
    // for rebind mainly:
    ConfigBag getAllConfigBag();
    ConfigBag getLocalConfigBag();
    @SuppressWarnings("rawtypes")
    Map<AttributeSensor, Object> getAllAttributes();
    EntityManagementSupport getManagementSupport();
    ManagementContext getManagementContext();
    Effector<?> getEffector(String effectorName);
    FeedSupport getFeedSupport();
    FeedSupport feeds();
    TagSupport getTagSupport();
    TagSupport tags();
    RebindSupport<EntityMemento> getRebindSupport();
    // for REST calls on read-only entities which want to resolve values
    ExecutionContext getExecutionContext();
    void setCatalogItemId(String id);
    
    /** more methods, but which are only on selected entities */
    public interface SpecialEntityTransientCopyInternal {
        // from Group
        Collection<Entity> getMembers();
        boolean hasMember(Entity member);
        Integer getCurrentSize();
    }

}
