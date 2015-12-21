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

import java.util.Map;
import java.util.Set;

import org.apache.brooklyn.api.entity.Application;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.Group;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.api.objs.BrooklynObject;
import org.apache.brooklyn.api.objs.BrooklynObject.RelationSupport;
import org.apache.brooklyn.api.policy.Policy;
import org.apache.brooklyn.api.relations.RelationshipType;
import org.apache.brooklyn.core.objs.BrooklynObjectInternal;
import org.apache.brooklyn.core.relations.AbstractBasicRelationSupport;
import org.apache.brooklyn.core.relations.RelationshipTypes;
import org.apache.brooklyn.util.collections.MutableMap;

import com.google.common.annotations.Beta;

/** TODO these relations are not used yet; see issue where this is introduced and email thread */
@Beta
public class EntityRelations<T extends BrooklynObject> {

    /** {@link #MANAGER_OF} indicates that one entity is the manager of another entity,
     * in the internal Brooklyn management hierarchy model.
     * Apart from root {@link Application} entities, every deployed entity must have exactly one manager.  
     * The inverse relationship is {@link #MANAGED_BY}. */ 
    public static final RelationshipType<Entity,Entity> MANAGER_OF = RelationshipTypes.newRelationshipPair(
        "manager", "managers", Entity.class, "manager_of", 
        "managed child", "managed children", Entity.class, "managed_by");
    /** Inverse of {@link #MANAGER_OF}. */
    public static final RelationshipType<Entity,Entity> MANAGED_BY = MANAGER_OF.getInverseRelationshipType();
    
    /** {@link #GROUP_CONTAINS} indicates that one entity, typically a {@link Group},
     * has zero or more entities which are labelled as "members" of that group entity.
     * What membership means will depend on the group entity.
     * An entity may be a member of any number of other entities.  
     * The inverse relationship is {@link #IN_GROUP}. */ 
    public static final RelationshipType<Entity,Entity> GROUP_CONTAINS = RelationshipTypes.newRelationshipPair(
        "group", "groups", Entity.class, "group_contains",
        "member", "members", Entity.class, "in_group");
    /** Inverse of {@link #GROUP_CONTAINS}. */
    public static final RelationshipType<Entity,Entity> IN_GROUP = GROUP_CONTAINS.getInverseRelationshipType();

    /** {@link #HAS_TARGET} indicates that one entity directs to one or more other entities.
     * What this targeting relationship means depends on the targetter.
     * The inverse relationship is {@link #TARGETTED_BY}. */
    public static final RelationshipType<Entity,Entity> HAS_TARGET = RelationshipTypes.newRelationshipPair(
        "targetter", "targetters", Entity.class, "has_target", 
        "target", "targets", Entity.class, "targetted_by");
    /** Inverse of {@link #HAS_TARGET}. */
    public static final RelationshipType<Entity,Entity> TARGETTED_BY = HAS_TARGET.getInverseRelationshipType();

    /** {@link #ACTIVE_PARENT_OF} indicates that one entity is should be considered as the logical parent of another,
     * e.g. for presentation purposes to the end user.
     * Frequently this relationship coincides with a {@link #MANAGED_BY} relationship, 
     * but sometimes some managed children are there for purposes the designers consider less important,
     * and they can choose to suppress the {@link #ACTIVE_PARENT_OF} relationship 
     * so that the active children is a subset of the managed children.
     * <p>
     * One recommended consideration is whether the child should be shown in a default tree view.
     * Whilst a user can always fina a way to see all managed children, 
     * it may be the case that only some of those are of primary interest,
     * and it is to identify those that this relationship exists.
     * <p>
     * It is permitted that an entity be an {@link #ACTIVE_PARENT_OF} an entity for which it is not a manager,
     * but in most cases a different relationship type is more appropriate where there is not also a management relationship.
     * <p> 
     * The inverse relationship is {@link #ACTIVE_CHILD_OF},
     * and an entity should normally be an {@link #ACTIVE_CHILD_OF} zero or one entities. */
    public static final RelationshipType<Entity,Entity> ACTIVE_PARENT_OF = RelationshipTypes.newRelationshipPair(
        "parent", "parents", Entity.class, "parent_of_active", 
        "active child", "active children", Entity.class, "active_child_of");
    /** Inverse of {@link #ACTIVE_PARENT_OF}. */
    public static final RelationshipType<Entity,Entity> ACTIVE_CHILD_OF = ACTIVE_PARENT_OF.getInverseRelationshipType();
    
    /** {@link #HAS_POLICY} indicates that an entity has a policy associated to it.
     * The inverse relationship is {@link #POLICY_FOR}. */
    public static final RelationshipType<Entity,Policy> HAS_POLICY = RelationshipTypes.newRelationshipPair(
        "entity", "entities", Entity.class, "has_policy", 
        "policy", "policies", Policy.class, "policy_for");
    /** Inverse of {@link #HAS_POLICY}. */
    public static final RelationshipType<Policy,Entity> POLICY_FOR = HAS_POLICY.getInverseRelationshipType();

    // ----
    
    // TODO replace by relations stored in catalog when catalog supports arbitrary types
    private static Map<String,RelationshipType<? extends BrooklynObject, ? extends BrooklynObject>> KNOWN_RELATIONSHIPS = MutableMap.of();
    private static void addRelationship(RelationshipType<? extends BrooklynObject, ? extends BrooklynObject> r) {
        KNOWN_RELATIONSHIPS.put(r.getRelationshipTypeName(), r);
        if (r.getInverseRelationshipType()!=null) {
            KNOWN_RELATIONSHIPS.put(r.getInverseRelationshipType().getRelationshipTypeName(), r.getInverseRelationshipType());
        }
    }
    static {
        addRelationship(MANAGER_OF);
        addRelationship(GROUP_CONTAINS);
        addRelationship(HAS_TARGET);
        addRelationship(HAS_POLICY);
    }
    
    /** Find the typed Relationship instance for the given relationship name, if known;
     * behaviour is not guaranteed by the API if not known (hence the Beta marker),
     * it may fail fast or return null or create a poor-man's relationship instance. 
     */
    @Beta
    public static RelationshipType<? extends BrooklynObject, ? extends BrooklynObject> lookup(ManagementContext mgmt, String relationshipTypeName) {
        if (relationshipTypeName==null) return null;
        RelationshipType<? extends BrooklynObject, ? extends BrooklynObject> result = KNOWN_RELATIONSHIPS.get(relationshipTypeName);
        if (result!=null) return result;
        
        /* TODO ultimately we'd like to support arbitrary relationships via persistence and lookup against the catalog;
         * however for now, so that we can persist nicely (without catalog items for relationships) 
         * we are smart about the relationships defined here, and we return a poor-man's version for items elsewhere.
         * 
         * for now, a poor-man's relationship; if not in catalog ultimately we should fail. */
        return RelationshipTypes.newRelationshipOneway("source", "sources", BrooklynObject.class, relationshipTypeName, "target", "targets", BrooklynObject.class);
    }
    
    /** 
     * As {@link RelationSupport#getRelationshipTypes()} for the given object.  Callers can use either method.
     * See {@link AbstractBasicRelationSupport} for a discussion of why double dispatch is used and both methods are present.
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static <T extends BrooklynObject> Set<RelationshipType<? super T,? extends BrooklynObject>> getRelationshipTypes(T source) {
        return (Set) ((BrooklynObjectInternal)source).relations().getLocalBackingStore().getRelationshipTypes();
    }

    /** 
     * As {@link RelationSupport#getRelations(RelationshipType)} for the given object.  Callers can use either method.
     * See {@link AbstractBasicRelationSupport} for a discussion of why double dispatch is used and both methods are present.
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static <T extends BrooklynObject,U extends BrooklynObject> Set<U> getRelations(RelationshipType<? super T,U> relationship, T source) {
        return (Set) ((BrooklynObjectInternal)source).relations().getLocalBackingStore().getRelations((RelationshipType)relationship);
    }

    /** 
     * As {@link RelationSupport#add(RelationshipType, BrooklynObject)} for the given object.  Callers can use either method.
     * See {@link AbstractBasicRelationSupport} for a discussion of why double dispatch is used and both methods are present.
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static <T extends BrooklynObject,U extends BrooklynObject> void add(T source, RelationshipType<? super T,? super U> relationship, U target) {
        ((BrooklynObjectInternal)source).relations().getLocalBackingStore().add((RelationshipType)relationship, target);
        if (relationship.getInverseRelationshipType()!=null)
            ((BrooklynObjectInternal)target).relations().getLocalBackingStore().add((RelationshipType)relationship.getInverseRelationshipType(), source);
    }

    /** 
     * As {@link RelationSupport#remove(RelationshipType, BrooklynObject)} for the given object.  Callers can use either method.
     * See {@link AbstractBasicRelationSupport} for a discussion of why double dispatch is used and both methods are present.
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static <T extends BrooklynObject,U extends BrooklynObject> void remove(T source, RelationshipType<? super T,? super U> relationship, U target) {
        ((BrooklynObjectInternal)source).relations().getLocalBackingStore().remove((RelationshipType)relationship, target);
        if (relationship.getInverseRelationshipType()!=null)
            ((BrooklynObjectInternal)target).relations().getLocalBackingStore().remove((RelationshipType)relationship.getInverseRelationshipType(), source);
    }

}
