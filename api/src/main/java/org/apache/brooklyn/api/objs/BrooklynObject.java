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
package org.apache.brooklyn.api.objs;

import java.util.Map;
import java.util.Set;

import javax.annotation.Nonnull;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.Group;
import org.apache.brooklyn.api.mgmt.SubscriptionContext;
import org.apache.brooklyn.api.mgmt.SubscriptionHandle;
import org.apache.brooklyn.api.mgmt.SubscriptionManager;
import org.apache.brooklyn.api.relations.RelationshipType;
import org.apache.brooklyn.api.sensor.Sensor;
import org.apache.brooklyn.api.sensor.SensorEventListener;

import com.google.common.annotations.Beta;
import com.google.common.collect.ImmutableMap;

/**
 * Super-type of entity, location, policy and enricher.
 */
public interface BrooklynObject extends Identifiable, Configurable {
    
    /**
     * A display name; recommended to be a concise single-line description.
     */
    String getDisplayName();

    /**
     * The catalog item ID this object was loaded from.
     * <p>
     * This can be used to understand the appropriate classloading context,
     * such as for versioning purposes, as well as meta-information such as 
     * branding (maybe you can even get an icon) and 
     * potentially things like resource lifecycle (if a software version is being sunsetted).
     * <p>
     * In some cases this may be set heuristically from context and so may not be accurate.
     * Callers can set an explicit catalog item ID if inferencing is not correct.
     */
    String getCatalogItemId();
    
    /** 
     * Tags are arbitrary objects which can be attached to an entity for subsequent reference.
     * They must not be null (as {@link ImmutableMap} may be used under the covers; also there is little point!);
     * and they should be amenable to our persistence (on-disk serialization) and our JSON serialization in the REST API.
     */
    TagSupport tags();

    /**
     * Subscriptions are the mechanism for receiving notifications of sensor-events (e.g. attribute-changed) from 
     * other entities.
     */
    SubscriptionSupport subscriptions();

    /**
     * Relations specify a typed, directed connection between two entities.
     * Generic type is overridden in sub-interfaces.
     */
    public RelationSupport<?> relations();
    
    public interface TagSupport {
        /**
         * @return An immutable copy of the set of tags on this entity. 
         * Note {@link #containsTag(Object)} will be more efficient,
         * and {@link #addTag(Object)} and {@link #removeTag(Object)} will not work on the returned set.
         */
        @Nonnull Set<Object> getTags();
        
        boolean containsTag(@Nonnull Object tag);
        
        boolean addTag(@Nonnull Object tag);
        
        boolean addTags(@Nonnull Iterable<?> tags);
        
        boolean removeTag(@Nonnull Object tag);
    }
    
    @Beta
    public interface SubscriptionSupport {
        /**
         * Allow us to subscribe to data from a {@link Sensor} on another entity.
         * 
         * @return a subscription id which can be used to unsubscribe
         *
         * @see SubscriptionManager#subscribe(Map, Entity, Sensor, SensorEventListener)
         */
        @Beta
        <T> SubscriptionHandle subscribe(Entity producer, Sensor<T> sensor, SensorEventListener<? super T> listener);
     
        /**
         * Allow us to subscribe to data from a {@link Sensor} on another entity.
         * 
         * @return a subscription id which can be used to unsubscribe
         *
         * @see SubscriptionManager#subscribe(Map, Entity, Sensor, SensorEventListener)
         */
        @Beta
        <T> SubscriptionHandle subscribe(Map<String, ?> flags, Entity producer, Sensor<T> sensor, SensorEventListener<? super T> listener);

        /** @see SubscriptionManager#subscribeToChildren(Map, Entity, Sensor, SensorEventListener) */
        @Beta
        <T> SubscriptionHandle subscribeToChildren(Entity parent, Sensor<T> sensor, SensorEventListener<? super T> listener);
     
        /** @see SubscriptionManager#subscribeToMembers(Group, Sensor, SensorEventListener) */
        @Beta
        <T> SubscriptionHandle subscribeToMembers(Group group, Sensor<T> sensor, SensorEventListener<? super T> listener);

        /**
         * Unsubscribes from the given producer.
         *
         * @see SubscriptionContext#unsubscribe(SubscriptionHandle)
         */
        @Beta
        boolean unsubscribe(Entity producer);

        /**
         * Unsubscribes the given handle.
         *
         * @see SubscriptionContext#unsubscribe(SubscriptionHandle)
         */
        @Beta
        boolean unsubscribe(Entity producer, SubscriptionHandle handle);
        
        /**
         * Unsubscribes the given handle.
         * 
         * It is (currently) more efficient to also pass in the producer -
         * see {@link SubscriptionSupport#unsubscribe(Entity, SubscriptionHandle)} 
         */
        boolean unsubscribe(SubscriptionHandle handle);
    }
    
    public interface RelationSupport<T extends BrooklynObject> {
        /** Adds a relationship of the given type from this object pointing at the given target, 
         * and ensures that the inverse relationship (if there is one) is present at the target pointing back at this object. 
         */
        public <U extends BrooklynObject> void add(RelationshipType<? super T,? super U> relationship, U target);
        
        /** Removes any and all relationships of the given type from this object pointing at the given target,
         * and ensures that the inverse relationships (if there are one) are also removed. 
         */
        public <U extends BrooklynObject> void remove(RelationshipType<? super T,? super U> relationship, U target);
        
        /** @return the {@link RelationshipType}s originating from this object */
        public Set<RelationshipType<? super T,? extends BrooklynObject>> getRelationshipTypes();
        
        /** @return the {@link BrooklynObject}s which are targets of the given {@link RelationshipType} */
        public <U extends BrooklynObject> Set<U> getRelations(RelationshipType<? super T,U> relationshipType);
    }
}
