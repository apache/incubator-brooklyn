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
package org.apache.brooklyn.core.entity;

import java.util.Collection;
import java.util.Map;

import org.apache.brooklyn.api.effector.Effector;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntityLocal;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.mgmt.ExecutionContext;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.api.mgmt.rebind.RebindSupport;
import org.apache.brooklyn.api.mgmt.rebind.Rebindable;
import org.apache.brooklyn.api.mgmt.rebind.mementos.EntityMemento;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.api.sensor.Feed;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.entity.internal.EntityConfigMap;
import org.apache.brooklyn.core.mgmt.internal.EntityManagementSupport;
import org.apache.brooklyn.core.objs.BrooklynObjectInternal;
import org.apache.brooklyn.util.core.config.ConfigBag;

import com.google.common.annotations.Beta;

/** 
 * Extended Entity interface with additional functionality that is purely-internal (i.e. intended 
 * for the brooklyn framework only).
 */
@Beta
public interface EntityInternal extends BrooklynObjectInternal, EntityLocal, Rebindable {
    
    void addLocations(Collection<? extends Location> locations);

    void removeLocations(Collection<? extends Location> locations);

    void clearLocations();

    /**
     * @deprecated since 0.8.0; use {@link SensorSupportInternal#setWithoutPublishing(AttributeSensor, Object)} via code like {@code sensors().setWithoutPublishing(attribute, val)}.
     */
    <T> T setAttributeWithoutPublishing(AttributeSensor<T> sensor, T val);

    /**
     * @deprecated since 0.7.0; instead just use methods on {@link ConfigurationSupportInternal} returned by {@link #config()}
     */
    @Deprecated
    EntityConfigMap getConfigMap();

    /**
     * @return a read-only copy of all the config key/value pairs on this entity.
     * 
     * @deprecated since 0.7.0; instead just use methods on {@link ConfigurationSupportInternal} returned by {@link #config()},
     * e.g. getBag().getAllConfigAsConfigKeyMap().
     */
    @Deprecated
    @Beta
    Map<ConfigKey<?>,Object> getAllConfig();

    /**
     * Returns a read-only view of all the config key/value pairs on this entity, backed by a string-based map, 
     * including config names that did not match anything on this entity.
     * 
     * @deprecated since 0.7.0; use {@link #config()}, such as {@code entity.config().getBag()}
     */
    @Deprecated
    ConfigBag getAllConfigBag();

    /**
     * Returns a read-only view of the local (i.e. not inherited) config key/value pairs on this entity, 
     * backed by a string-based map, including config names that did not match anything on this entity.
     * 
     * @deprecated since 0.7.0; use {@link #config()}, such as {@code entity.config().getLocalBag()}
     */
    @Deprecated
    ConfigBag getLocalConfigBag();

    /**
     * @deprecated since 0.8.0; use {@link SensorSupportInternal#getAll()} via code like {@code sensors().getAll()}.
     */
    @Beta
    Map<AttributeSensor, Object> getAllAttributes();

    /**
     * @deprecated since 0.8.0; use {@link SensorSupportInternal#remove(AttributeSensor)} via code like {@code sensors().remove(attribute)}.
     */
    @Beta
    void removeAttribute(AttributeSensor<?> attribute);

    /**
     * 
     * @deprecated since 0.7.0; use {@link #config()}, such as {@code entity.config().refreshInheritedConfig()}
     */
    @Deprecated
    void refreshInheritedConfig();

    /**
     * Must be called before the entity is started.
     * 
     * @return this entity (i.e. itself)
     */
    @Beta // for internal use only
    EntityInternal configure(Map flags);

    /** 
     * @return Routings for accessing and inspecting the management context of the entity
     */
    EntityManagementSupport getManagementSupport();

    /**
     * Should be invoked at end-of-life to clean up the item.
     */
    @Beta
    void destroy();
    
    /** 
     * Returns the management context for the entity. If the entity is not yet managed, some 
     * operations on the management context will fail. 
     * 
     * Do not cache this object; instead call getManagementContext() each time you need to use it.
     */
    ManagementContext getManagementContext();

    /** 
     * Returns the task execution context for the entity. If the entity is not yet managed, some 
     * operations on the management context will fail.
     * 
     * Do not cache this object; instead call getExecutionContext() each time you need to use it.
     */    
    ExecutionContext getExecutionContext();

    /** returns the dynamic type corresponding to the type of this entity instance */
    @Beta
    EntityDynamicType getMutableEntityType();

    /** returns the effector registered against a given name */
    @Beta
    Effector<?> getEffector(String effectorName);
    
    FeedSupport feeds();
    
    /**
     * @since 0.7.0-M2
     * @deprecated since 0.7.0-M2; use {@link #feeds()}
     */
    @Deprecated
    FeedSupport getFeedSupport();

    Map<String, String> toMetadataRecord();
    
    /**
     * Users are strongly discouraged from calling or overriding this method.
     * It is for internal calls only, relating to persisting/rebinding entities.
     * This method may change (or be removed) in a future release without notice.
     */
    @Override
    @Beta
    RebindSupport<EntityMemento> getRebindSupport();

    /**
     * Can be called to request that the entity be persisted.
     * This persistence may happen asynchronously, or may not happen at all if persistence is disabled.
     */
    void requestPersist();
    
    @Override
    SensorSupportInternal sensors();

    @Override
    PolicySupportInternal policies();

    @Override
    EnricherSupportInternal enrichers();

    @Beta
    public interface SensorSupportInternal extends Entity.SensorSupport {
        /**
         * 
         * Like {@link EntityLocal#setAttribute(AttributeSensor, Object)}, except does not publish an attribute-change event.
         */
        <T> T setWithoutPublishing(AttributeSensor<T> sensor, T val);
        
        @Beta
        Map<AttributeSensor<?>, Object> getAll();

        @Beta
        void remove(AttributeSensor<?> attribute);
    }

    public interface FeedSupport {
        Collection<Feed> getFeeds();
        
        /**
         * Adds the given feed to this entity. The feed will automatically be re-added on brooklyn restart.
         */
        <T extends Feed> T addFeed(T feed);
        
        /**
         * Removes the given feed from this entity. 
         * @return True if the feed existed at this entity; false otherwise
         */
        boolean removeFeed(Feed feed);
        
        /**
         * Removes all feeds from this entity.
         * Use with caution as some entities automatically register feeds; this will remove those feeds as well.
         * @return True if any feeds existed at this entity; false otherwise
         */
        boolean removeAllFeeds();
    }
    
    @Beta
    public interface PolicySupportInternal extends Entity.PolicySupport {
        /**
         * Removes all policy from this entity. 
         * @return True if any policies existed at this entity; false otherwise
         */
        boolean removeAllPolicies();
    }
    
    @Beta
    public interface EnricherSupportInternal extends Entity.EnricherSupport {
        /**
         * Removes all enricher from this entity.
         * Use with caution as some entities automatically register enrichers; this will remove those enrichers as well.
         * @return True if any enrichers existed at this entity; false otherwise
         */
        boolean removeAllEnrichers();
    }
}
