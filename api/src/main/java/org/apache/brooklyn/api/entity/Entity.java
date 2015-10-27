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
package org.apache.brooklyn.api.entity;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

import javax.annotation.Nullable;

import org.apache.brooklyn.api.effector.Effector;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.mgmt.Task;
import org.apache.brooklyn.api.objs.BrooklynObject;
import org.apache.brooklyn.api.objs.EntityAdjunct;
import org.apache.brooklyn.api.policy.Policy;
import org.apache.brooklyn.api.policy.PolicySpec;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.api.sensor.Enricher;
import org.apache.brooklyn.api.sensor.EnricherSpec;
import org.apache.brooklyn.api.sensor.Feed;
import org.apache.brooklyn.api.sensor.Sensor;
import org.apache.brooklyn.api.sensor.SensorEvent;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.config.ConfigKey.HasConfigKey;
import org.apache.brooklyn.util.guava.Maybe;

import com.google.common.annotations.Beta;
import com.google.common.base.Function;

/**
 * The basic interface for a Brooklyn entity.
 * <p>
 * Implementors of entities are strongly encouraged to extend {@link org.apache.brooklyn.core.entity.AbstractEntity}.
 * <p>
 * To instantiate an entity, see {@code managementContext.getEntityManager().createEntity(entitySpec)}.
 * Also see {@link org.apache.brooklyn.core.entity.factory.ApplicationBuilder}, 
 * {@link org.apache.brooklyn.core.entity.AbstractEntity#addChild(EntitySpec)}, and
 * {@link org.apache.brooklyn.api.entity.EntitySpec}.
 * <p>
 * 
 * @see org.apache.brooklyn.core.entity.AbstractEntity
 */
public interface Entity extends BrooklynObject {
    /**
     * The unique identifier for this entity.
     */
    @Override
    String getId();
    
    /**
     * Returns the creation time for this entity, in UTC.
     */
    long getCreationTime();
    
    /**
     * A display name; recommended to be a concise single-line description.
     */
    String getDisplayName();
    
    /** 
     * A URL pointing to an image which can be used to represent this entity.
     */
    @Nullable String getIconUrl();
    
    /**
     * Information about the type of this entity; analogous to Java's object.getClass.
     */
    EntityType getEntityType();
    
    /**
     * @return the {@link Application} this entity is registered with, or null if not registered.
     */
    Application getApplication();

    /**
     * @return the id of the {@link Application} this entity is registered with, or null if not registered.
     */
    String getApplicationId();

    /**
     * The parent of this entity, null if no parent.
     *
     * The parent is normally the entity responsible for creating/destroying/managing this entity.
     *
     * @see #setParent(Entity)
     * @see #clearParent
     */
    Entity getParent();
    
    /** 
     * Return the entities that are children of (i.e. "owned by") this entity
     */
    Collection<Entity> getChildren();
    
    /**
     * Sets the entity's display name.
     */
    void setDisplayName(String displayName);

    /**
     * Sets the parent (i.e. "owner") of this entity. Returns this entity, for convenience.
     *
     * @see #getParent
     * @see #clearParent
     */
    Entity setParent(Entity parent);
    
    /**
     * Clears the parent (i.e. "owner") of this entity. Also cleans up any references within its parent entity.
     *
     * @see #getParent
     * @see #setParent
     */
    void clearParent();
    
    /** 
     * Add a child {@link Entity}, and set this entity as its parent,
     * returning the added child.
     * <p>
     * As with {@link #addChild(EntitySpec)} the child is <b>not</b> brought under management
     * as part of this call.  It should not be managed prior to this call either.
     */
    <T extends Entity> T addChild(T child);
    
    /** 
     * Creates an {@link Entity} from the given spec and adds it, setting this entity as the parent,
     * returning the added child.
     * <p>
     * The added child is <b>not</b> managed as part of this call, even if the parent is managed,
     * so if adding post-management an explicit call to manage the child will be needed;
     * see the convenience method <code>Entities.manage(...)</code>. 
     * */
    <T extends Entity> T addChild(EntitySpec<T> spec);
    
    /** 
     * Removes the specified child {@link Entity}; its parent will be set to null.
     * 
     * @return True if the given entity was contained in the set of children
     */
    boolean removeChild(Entity child);
    
    /**
     * @return an immutable thread-safe view of the policies.
     * 
     * @deprecated since 0.9.0; see {@link PolicySupport#getPolicies()}
     */
    @Deprecated
    Collection<Policy> getPolicies();
    
    /**
     * @return an immutable thread-safe view of the enrichers.
     * 
     * @deprecated since 0.9.0; see {@link EnricherSupport#getEnrichers()}
     */
    @Deprecated
    Collection<Enricher> getEnrichers();
    
    /**
     * The {@link Collection} of {@link Group}s that this entity is a member of.
     *
     * Groupings can be used to allow easy management/monitoring of a group of entities.
     * 
     * @deprecated since 0.9.0; see {@link GroupSupport#getGroups()} and {@link #groups()}
     */
    @Deprecated
    Collection<Group> getGroups();

    /**
     * Add this entity as a member of the given {@link Group}. Called by framework.
     * <p>
     * Users should call {@link Group#addMember(Entity)} instead; this method will then 
     * automatically be called. However, the reverse is not true (calling this method will 
     * not tell the group; this behaviour may change in a future release!)
     * 
     * @deprecated since 0.9.0; see {@link GroupSupport#add()} and {@link #groups()}
     */
    @Deprecated
    void addGroup(Group group);

    /**
     * Removes this entity as a member of the given {@link Group}. Called by framework.
     * <p>
     * Users should call {@link Group#removeMember(Entity)} instead; this method will then 
     * automatically be called. However, the reverse is not true (calling this method will 
     * not tell the group; this behaviour may change in a future release!)
     * 
     * @deprecated since 0.9.0; see {@link GroupSupport#remove()} and {@link #groups()}
     */
    @Deprecated
    void removeGroup(Group group);

    /**
     * Return all the {@link Location}s this entity is deployed to.
     */
    Collection<Location> getLocations();

    /**
     * Convenience for calling {@link SensorSupport#get(AttributeSensor)},
     * via code like {@code sensors().get(key)}.
     */
    <T> T getAttribute(AttributeSensor<T> sensor);
    
    /**
     * @see {@link #getConfig(ConfigKey)}
     */
    <T> T getConfig(HasConfigKey<T> key);
    
    /**
     * Returns the uncoerced value for this config key as set on this entity, if available,
     * not following any inheritance chains and not taking any default.
     * 
     * @deprecated since 0.7.0; use {@code ((EntityInternal)entity).config().getRaw()} or
     *             {@code ((EntityInternal)entity).config().getLocalRaw()}
     */
    @Deprecated
    Maybe<Object> getConfigRaw(ConfigKey<?> key, boolean includeInherited);
    
    /**
     * @see {@link #getConfigRaw(ConfigKey, boolean)}.
     * 
     * @deprecated since 0.7.0
     */
    @Deprecated
    Maybe<Object> getConfigRaw(HasConfigKey<?> key, boolean includeInherited);

    /**
     * Invokes the given effector, with the given parameters to that effector.
     */
    <T> Task<T> invoke(Effector<T> eff, Map<String,?> parameters);
    
    /**
     * Adds the given policy to this entity. Also calls policy.setEntity if available.
     * 
     * @deprecated since 0.9.0; see {@link PolicySupport#add(Policy)}
     */
    @Deprecated
    void addPolicy(Policy policy);
    
    /**
     * Adds the given policy to this entity. Also calls policy.setEntity if available.
     * 
     * @deprecated since 0.9.0; see {@link PolicySupport#add(PolicySpec)}
     */
    @Deprecated
    <T extends Policy> T addPolicy(PolicySpec<T> policy);
    
    /**
     * Removes the given policy from this entity. 
     * @return True if the policy existed at this entity; false otherwise
     * 
     * @deprecated since 0.9.0; see {@link PolicySupport#remove(Policy)}
     */
    @Deprecated
    boolean removePolicy(Policy policy);
    
    /**
     * Adds the given enricher to this entity. Also calls enricher.setEntity if available.
     * 
     * @deprecated since 0.9.0; see {@link EnricherSupport#add(Enricher)}
     */
    @Deprecated
    void addEnricher(Enricher enricher);
    
    /**
     * Adds the given enricher to this entity. Also calls enricher.setEntity if available.
     * 
     * @deprecated since 0.9.0; see {@link EnricherSupport#add(EnricherSpec)}
     */
    @Deprecated
    <T extends Enricher> T addEnricher(EnricherSpec<T> enricher);
    
    /**
     * Removes the given enricher from this entity. 
     * @return True if the policy enricher at this entity; false otherwise
     * 
     * @deprecated since 0.9.0; see {@link EnricherSupport#remove(Enricher)}
     */
    @Deprecated
    boolean removeEnricher(Enricher enricher);
    
    /**
     * Adds the given feed to this entity. Also calls feed.setEntity if available.
     */
    <T extends Feed> T addFeed(T feed);
    
    SensorSupport sensors();

    PolicySupport policies();

    EnricherSupport enrichers();

    GroupSupport groups();

    RelationSupport<Entity> relations();
    
    @Beta
    public interface SensorSupport {

        /**
         * Gets the value of the given attribute on this entity, or null if has not been set.
         *
         * Attributes can be things like workrate and status information, as well as
         * configuration (e.g. url/jmxHost/jmxPort), etc.
         */
        <T> T get(AttributeSensor<T> key);

        /**
         * Sets the {@link AttributeSensor} data for the given attribute to the specified value.
         * 
         * This can be used to "enrich" the entity, such as adding aggregated information, 
         * rolling averages, etc.
         * 
         * @return the old value for the attribute (possibly {@code null})
         */
        <T> T set(AttributeSensor<T> attribute, T val);

        /**
         * Atomically modifies the {@link AttributeSensor}, ensuring that only one modification is done
         * at a time.
         * 
         * If the modifier returns {@link Maybe#absent()} then the attribute will be
         * left unmodified, and the existing value will be returned.
         * 
         * For details of the synchronization model used to achieve this, refer to the underlying 
         * attribute store (e.g. AttributeMap).
         * 
         * @return the old value for the attribute (possibly {@code null})
         * @since 0.7.0-M2
         */
        @Beta
        <T> T modify(AttributeSensor<T> attribute, Function<? super T, Maybe<T>> modifier);

        /**
         * Emits a {@link SensorEvent} event on behalf of this entity (as though produced by this entity).
         * <p>
         * Note that for attribute sensors it is nearly always recommended to use setAttribute, 
         * as this method will not update local values.
         */
        <T> void emit(Sensor<T> sensor, T value);
    }
    
    public interface AdjunctSupport<T extends EntityAdjunct> extends Iterable<T> {
        /**
         * @return A read-only thread-safe iterator over all the instances.
         */
        Iterator<T> iterator();
        
        /**
         * Adds an instance.
         */
        void add(T val);
        
        /**
         * Removes an instance.
         */
        boolean remove(T val);
    }
    
    @Beta
    public interface PolicySupport extends AdjunctSupport<Policy> {
        /**
         * Adds the given policy to this entity. Also calls policy.setEntity if available.
         */
        @Override
        void add(Policy policy);
        
        /**
         * Removes the given policy from this entity. 
         * @return True if the policy existed at this entity; false otherwise
         */
        @Override
        boolean remove(Policy policy);
        
        /**
         * Adds the given policy to this entity. Also calls policy.setEntity if available.
         */
        <T extends Policy> T add(PolicySpec<T> enricher);
    }
    
    @Beta
    public interface EnricherSupport extends AdjunctSupport<Enricher> {
        /**
         * Adds the given enricher to this entity. Also calls enricher.setEntity if available.
         */
        @Override
        void add(Enricher enricher);
        
        /**
         * Removes the given enricher from this entity. 
         * @return True if the policy enricher at this entity; false otherwise
         */
        @Override
        boolean remove(Enricher enricher);
        
        /**
         * Adds the given enricher to this entity. Also calls enricher.setEntity if available.
         */
        <T extends Enricher> T add(EnricherSpec<T> enricher);
    }

    /**
     * For managing/querying the group membership of this entity. 
     * 
     * Groupings can be used to allow easy management/monitoring of a group of entities.
     * 
     * To add/remove this entity from a group, users should call {@link Group#addMember(Entity)} 
     * and {@link Group#removeMember(Entity)}. In a future release, add/remove methods may be
     * added here.
     */
    @Beta
    public interface GroupSupport extends Iterable<Group> {
        /**
         * A read-only thread-safe iterator over all the {@link Group}s that this entity is a member of.
         */
        @Override
        Iterator<Group> iterator();
    }
}
