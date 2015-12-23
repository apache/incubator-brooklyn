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
package org.apache.brooklyn.core.entity.internal;

import java.util.Collection;
import java.util.Map;

import javax.annotation.Nullable;

import org.apache.brooklyn.api.effector.Effector;
import org.apache.brooklyn.api.entity.Application;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntityType;
import org.apache.brooklyn.api.entity.Group;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.mgmt.ExecutionContext;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.api.mgmt.rebind.RebindSupport;
import org.apache.brooklyn.api.mgmt.rebind.mementos.EntityMemento;
import org.apache.brooklyn.api.objs.BrooklynObject.TagSupport;
import org.apache.brooklyn.api.policy.Policy;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.api.sensor.Enricher;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.config.ConfigKey.HasConfigKey;
import org.apache.brooklyn.core.entity.EntityInternal;
import org.apache.brooklyn.core.entity.EntityInternal.FeedSupport;
import org.apache.brooklyn.core.mgmt.internal.EntityManagementSupport;
import org.apache.brooklyn.core.objs.proxy.EntityProxyImpl;
import org.apache.brooklyn.util.core.config.ConfigBag;
import org.apache.brooklyn.util.guava.Maybe;

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

    // TODO For feeds() and config(), need to ensure mutator methods on returned object are not invoked.
    
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
    TagSupport tags();
    String getCatalogItemId();

    
    // from entity local
    
    @Deprecated <T> T getConfig(ConfigKey<T> key, T defaultValue);
    @Deprecated <T> T getConfig(HasConfigKey<T> key, T defaultValue);

    
    // from EntityInternal:
    
    @Deprecated EntityConfigMap getConfigMap();
    @Deprecated Map<ConfigKey<?>,Object> getAllConfig();
    // for rebind mainly:
    @Deprecated ConfigBag getAllConfigBag();
    @Deprecated ConfigBag getLocalConfigBag();
    @SuppressWarnings("rawtypes")
    Map<AttributeSensor, Object> getAllAttributes();
    EntityManagementSupport getManagementSupport();
    ManagementContext getManagementContext();
    Effector<?> getEffector(String effectorName);
    @Deprecated FeedSupport getFeedSupport();
    FeedSupport feeds();
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
