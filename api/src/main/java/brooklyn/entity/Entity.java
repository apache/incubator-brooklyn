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
package brooklyn.entity;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.common.collect.ImmutableMap;

import brooklyn.config.ConfigKey;
import brooklyn.config.ConfigKey.HasConfigKey;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.trait.Identifiable;
import brooklyn.event.AttributeSensor;
import brooklyn.location.Location;
import brooklyn.management.Task;
import brooklyn.policy.Enricher;
import brooklyn.policy.EnricherSpec;
import brooklyn.policy.Policy;
import brooklyn.policy.PolicySpec;
import brooklyn.util.guava.Maybe;

/**
 * The basic interface for a Brooklyn entity.
 * <p>
 * Implementors of entities are strongly encouraged to extend {@link brooklyn.entity.basic.AbstractEntity}.
 * <p>
 * To instantiate an entity, see {@code managementContext.getEntityManager().createEntity(entitySpec)}.
 * Also see {@link brooklyn.entity.basic.ApplicationBuilder}, 
 * {@link brooklyn.entity.basic.AbstractEntity#addChild(EntitySpec)}, and
 * {@link brooklyn.entity.proxying.EntitySpec}.
 * <p>
 * 
 * @see brooklyn.entity.basic.AbstractEntity
 */
public interface Entity extends Identifiable {
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
     * A URL pointing to an image which can be used to represent this icon.
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
     */
    <T extends Entity> T addChild(T child);
    
    /** 
     * Creates an {@link Entity} from the given spec and adds it, setting this entity as the parent,
     * returning the added child. */
    <T extends Entity> T addChild(EntitySpec<T> spec);
    
    /** 
     * Removes the specified child {@link Entity}; its parent will be set to null.
     * 
     * @return True if the given entity was contained in the set of children
     */
    boolean removeChild(Entity child);
    
    /**
     * @return an immutable thread-safe view of the policies.
     */
    Collection<Policy> getPolicies();
    
    /**
     * @return an immutable thread-safe view of the enrichers.
     */
    Collection<Enricher> getEnrichers();
    
    /**
     * The {@link Collection} of {@link Group}s that this entity is a member of.
     *
     * Groupings can be used to allow easy management/monitoring of a group of entities.
     */
    Collection<Group> getGroups();

    /**
     * Add this entity as a member of the given {@link Group}. Called by framework.
     * <p>
     * Users should call {@link Group#addMember(Entity)} instead; this method will then 
     * automatically be called. However, the reverse is not true (calling this method will 
     * not tell the group; this behaviour may change in a future release!)
     */
    void addGroup(Group group);

    /**
     * Removes this entity as a member of the given {@link Group}. Called by framework.
     * <p>
     * Users should call {@link Group#removeMember(Entity)} instead; this method will then 
     * automatically be called. However, the reverse is not true (calling this method will 
     * not tell the group; this behaviour may change in a future release!)
     */
    void removeGroup(Group group);

    /**
     * Return all the {@link Location}s this entity is deployed to.
     */
    Collection<Location> getLocations();

    /**
     * Gets the value of the given attribute on this entity, or null if has not been set.
     *
     * Attributes can be things like workrate and status information, as well as
     * configuration (e.g. url/jmxHost/jmxPort), etc.
     */
    <T> T getAttribute(AttributeSensor<T> sensor);

    /**
     * Gets the given configuration value for this entity, in the following order of preference:
     * <li> value (including null) explicitly set on the entity
     * <li> value (including null) explicitly set on an ancestor (inherited)
     * <li> a default value (including null) on the best equivalent static key of the same name declared on the entity
     *      (where best equivalence is defined as preferring a config key which extends another, 
     *      as computed in EntityDynamicType.getConfigKeys)
     * <li> a default value (including null) on the key itself
     * <li> null
     */
    <T> T getConfig(ConfigKey<T> key);
    <T> T getConfig(HasConfigKey<T> key);
    
    
    /**
     * Returns the uncoerced value for this config key as set on this entity, if available,
     * not following any inheritance chains and not taking any default.
     */
    Maybe<Object> getConfigRaw(ConfigKey<?> key, boolean includeInherited);
    Maybe<Object> getConfigRaw(HasConfigKey<?> key, boolean includeInherited);

    /**
     * Invokes the given effector, with the given parameters to that effector.
     */
    <T> Task<T> invoke(Effector<T> eff, Map<String,?> parameters);
    
    /**
     * Adds the given policy to this entity. Also calls policy.setEntity if available.
     */
    void addPolicy(Policy policy);
    
    /**
     * Adds the given policy to this entity. Also calls policy.setEntity if available.
     */
    <T extends Policy> T addPolicy(PolicySpec<T> enricher);
    
    /**
     * Removes the given policy from this entity. 
     * @return True if the policy existed at this entity; false otherwise
     */
    boolean removePolicy(Policy policy);
    
    /**
     * Adds the given enricher to this entity. Also calls enricher.setEntity if available.
     */
    void addEnricher(Enricher enricher);
    
    /**
     * Adds the given enricher to this entity. Also calls enricher.setEntity if available.
     */
    <T extends Enricher> T addEnricher(EnricherSpec<T> enricher);
    
    /**
     * Removes the given enricher from this entity. 
     * @return True if the policy enricher at this entity; false otherwise
     */
    boolean removeEnricher(Enricher enricher);
    
    /** 
     * Tags are arbitrary objects which can be attached to an entity for subsequent reference.
     * They must not be null (as {@link ImmutableMap} may be used under the covers; also there is little point!);
     * and they should be amenable to our persistence (on-disk serialization) and our JSON serialization in the REST API.
     * 
     * @return An immutable copy of the set of tags on this entity. 
     * Note {@link #containsTag(Object)} will be more efficient,
     * and {@link #addTag(Object)} and {@link #removeTag(Object)} will not work. */
    Set<Object> getTags();
    boolean addTag(@Nonnull Object tag);
    boolean removeTag(@Nonnull Object tag);
    boolean containsTag(@Nonnull Object tag);

}
