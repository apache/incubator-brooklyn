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

import java.util.Iterator;
import java.util.List;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.objs.EntityAdjunct;
import org.apache.brooklyn.api.sensor.Enricher;
import org.apache.brooklyn.core.entity.lifecycle.ServiceStateLogic.ComputeServiceIndicatorsFromChildrenAndMembers;
import org.apache.brooklyn.core.entity.lifecycle.ServiceStateLogic.ComputeServiceState;
import org.apache.brooklyn.core.entity.lifecycle.ServiceStateLogic.ServiceNotUpLogic;
import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.guava.Maybe;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

/**
 * Convenience methods for working with entity adjunts.
 */
public class EntityAdjuncts {

    public static <T extends EntityAdjunct> Maybe<T> tryFindWithUniqueTag(Iterable<T> adjuncts, Object tag) {
        Preconditions.checkNotNull(tag, "tag");
        for (T adjunct: adjuncts)
            if (tag.equals(adjunct.getUniqueTag())) 
                return Maybe.of(adjunct);
        return Maybe.absent("Not found with tag "+tag);
    }
    
    public static final List<String> SYSTEM_ENRICHER_UNIQUE_TAGS = ImmutableList.of(
        ServiceNotUpLogic.DEFAULT_ENRICHER_UNIQUE_TAG,
        ComputeServiceState.DEFAULT_ENRICHER_UNIQUE_TAG,
        ComputeServiceIndicatorsFromChildrenAndMembers.DEFAULT_UNIQUE_TAG,
        ComputeServiceIndicatorsFromChildrenAndMembers.DEFAULT_UNIQUE_TAG_UP);
    
    public static List<Enricher> getNonSystemEnrichers(Entity entity) {
        List<Enricher> result = MutableList.copyOf(entity.enrichers());
        Iterator<Enricher> ri = result.iterator();
        while (ri.hasNext()) {
            if (isSystemEnricher(ri.next())) ri.remove();
        }
        return result;
    }

    public static boolean isSystemEnricher(Enricher enr) {
        if (enr.getUniqueTag()==null) return false;
        if (SYSTEM_ENRICHER_UNIQUE_TAGS.contains(enr.getUniqueTag())) return true;
        return false;
    }
    
}
