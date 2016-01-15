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

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import org.apache.brooklyn.api.objs.BrooklynObject;
import org.apache.brooklyn.api.objs.BrooklynObject.RelationSupport;
import org.apache.brooklyn.api.relations.RelationshipType;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.collections.MutableSet;

import com.google.common.base.Supplier;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;

public class ByObjectBasicRelationSupport<SourceType extends BrooklynObject> extends AbstractBasicRelationSupport<SourceType> {

    DataForBasicRelations<SourceType> data;
    
    public ByObjectBasicRelationSupport(SourceType source, Runnable relationChangeCallback) { 
        super(source); 
        data = new DataForBasicRelations<SourceType>(relationChangeCallback);
    }
    
    @Override
    public RelationSupport<SourceType> getLocalBackingStore() {
        return data;
    }

    public static class DataForBasicRelations<T extends BrooklynObject> implements RelationSupport<T> {
        
        Runnable relationChangeCallback;
        
        public DataForBasicRelations(Runnable relationChangeCallback) {
            this.relationChangeCallback = relationChangeCallback;
        }
        
        // TODO for now, relationships are stored here (and persisted); ideally we'd look them up in catalog
        private Map<String,RelationshipType<? super T,? extends BrooklynObject>> relationshipTypes = MutableMap.of();
        
        private Multimap<String,BrooklynObject> relations = Multimaps.newMultimap(MutableMap.<String,Collection<BrooklynObject>>of(), 
            new Supplier<Collection<BrooklynObject>>() {
                public Collection<BrooklynObject> get() {
                    return MutableSet.of();
                }
            });

        public Set<RelationshipType<? super T,? extends BrooklynObject>> getRelationshipTypes() {
            synchronized (relations) {
                return MutableSet.copyOf(relationshipTypes.values());
            }
        }
        
        @SuppressWarnings("unchecked") @Override 
        public <U extends BrooklynObject> Set<U> getRelations(RelationshipType<? super T, U> relationship) {
            synchronized (relations) {
                return (Set<U>)MutableSet.copyOf(relations.get(relationship.getRelationshipTypeName()));
            }
        }

        @SuppressWarnings({ "unchecked", "rawtypes" })
        @Override
        public <U extends BrooklynObject> void add(RelationshipType<? super T,? super U> relationship, U target) {
            synchronized (relations) {
                relationshipTypes.put(relationship.getRelationshipTypeName(), (RelationshipType)relationship);
                relations.put(relationship.getRelationshipTypeName(), target);
            }
            onRelationsChanged();
        }

        @Override
        public <U extends BrooklynObject> void remove(RelationshipType<? super T,? super U> relationship, U target) {
            synchronized (relations) {
                relations.remove(relationship.getRelationshipTypeName(), target);
            }
            onRelationsChanged();
        }

        protected void onRelationsChanged() {
            if (relationChangeCallback!=null) relationChangeCallback.run();
        }
    }

}
