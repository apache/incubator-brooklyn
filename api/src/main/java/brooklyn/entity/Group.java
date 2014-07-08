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

import brooklyn.entity.proxying.EntitySpec;

/**
 * An {@link Entity} that groups together other entities.
 * 
 * The grouping can be for any purpose, such as allowing easy management/monitoring of
 * a group of entities. The grouping could be static (i.e. a fixed set of entities)
 * or dynamic (i.e. contains all entities that match some filter).
 */
public interface Group extends Entity {
    /**
     * Return the entities that are members of this group.
     */
    Collection<Entity> getMembers();

    /**
     * @return True if it is a member of this group.
     */
    boolean hasMember(Entity member);

    /**
     * Adds the given member, returning true if this modifies the set of members (i.e. it was not already a member).
     */
    boolean addMember(Entity member);
 
    /**
     * Removes the given member, returning true if this modifies the set of members (i.e. it was a member).
     */
    boolean removeMember(Entity member);
    
    /**
     * @return The number of members in this group.
     */
    Integer getCurrentSize();
    
    /** As {@link #addChild(EntitySpec)} followed by {@link #addMember(Entity)} */
    <T extends Entity> T addMemberChild(EntitySpec<T> spec);
    
    /** As {@link #addChild(Entity)} followed by {@link #addMember(Entity)} */
    <T extends Entity> T addMemberChild(T child);
    
    /** As in super, but note this does NOT by default add it as a member; see {@link #addMemberChild(EntitySpec)} */
    @Override
    <T extends Entity> T addChild(EntitySpec<T> spec);
    
    /** As in super, but note this does NOT by default add it as a member; see {@link #addMemberChild(Entity)} */
    @Override
    <T extends Entity> T addChild(T child);

}
