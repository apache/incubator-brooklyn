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
package org.apache.brooklyn.core.relations;

import java.util.Set;

import org.apache.brooklyn.api.objs.BrooklynObject;
import org.apache.brooklyn.core.entity.EntityRelations;
import org.apache.brooklyn.core.objs.BrooklynObjectInternal.RelationSupportInternal;

import brooklyn.basic.relations.RelationshipType;

/** This abstract impl delegates to {@link EntityRelations} for all changes, routing through a local backing store.
 * This allows us to make the changes in both directions simultaneously when a relationship is bi-directional,
 * and should facilitate changing a backing datastore or remote instances when that is supported.
 * <p>
 * Currently it can be implemented without it, simplifying things a bit (avoiding the double dispatch)
 * apart from the {@link #add(RelationshipType, BrooklynObject)} method triggering the reverse addition
 * if it isn't already present. TBD which is better (and the internal call to get the backing store is 
 * marked as Beta). */
public abstract class AbstractBasicRelationSupport<SourceType extends BrooklynObject> implements RelationSupportInternal<SourceType> {

    final SourceType source;
    
    public AbstractBasicRelationSupport(SourceType source) { this.source = source; }
        
    @Override
    public Set<RelationshipType<? super SourceType, ? extends BrooklynObject>> getRelationshipTypes() {
        return EntityRelations.getRelationshipTypes(source);
    }
    
    @Override
    public <U extends BrooklynObject> Set<U> getRelations(RelationshipType<? super SourceType, U> relationship) {
        return EntityRelations.getRelations(relationship, source);
    }

    @Override
    public <U extends BrooklynObject> void add(RelationshipType<? super SourceType, ? super U> relationship, U target) {
        EntityRelations.add(source, relationship, target);
    }

    @Override
    public <U extends BrooklynObject> void remove(RelationshipType<? super SourceType, ? super U> relationship, U target) {
        EntityRelations.remove(source, relationship, target);
    }

}
