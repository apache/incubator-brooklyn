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

import java.util.Collections;
import java.util.Set;

import org.apache.brooklyn.api.objs.BrooklynObject;
import org.apache.brooklyn.api.objs.BrooklynObject.RelationSupport;
import org.apache.brooklyn.api.relations.RelationshipType;
import org.apache.brooklyn.core.objs.BrooklynObjectInternal.RelationSupportInternal;

public final class EmptyRelationSupport<SourceType extends BrooklynObject> implements RelationSupportInternal<SourceType> {

    final SourceType source;
    
    public EmptyRelationSupport(SourceType source) { this.source = source; }
        
    @Override
    public Set<RelationshipType<? super SourceType, ? extends BrooklynObject>> getRelationshipTypes() {
        return Collections.emptySet();
    }
    
    @Override
    public <U extends BrooklynObject> Set<U> getRelations(RelationshipType<? super SourceType, U> relationship) {
        return Collections.emptySet();
    }

    @Override
    public <U extends BrooklynObject> void add(RelationshipType<? super SourceType, ? super U> relationship, U target) {
        throw new UnsupportedOperationException("Relations not available on "+source);
    }

    @Override
    public <U extends BrooklynObject> void remove(RelationshipType<? super SourceType, ? super U> relationship, U target) {
    }

    @Override
    public RelationSupport<SourceType> getLocalBackingStore() {
        throw new UnsupportedOperationException("Relations not available on "+source);
    }

}
