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
package brooklyn.entity.basic;

import java.util.Iterator;
import java.util.List;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.ServiceStateLogic.ComputeServiceIndicatorsFromChildrenAndMembers;
import brooklyn.entity.basic.ServiceStateLogic.ComputeServiceState;
import brooklyn.entity.basic.ServiceStateLogic.ServiceNotUpLogic;
import brooklyn.policy.Enricher;
import brooklyn.policy.EntityAdjunct;
import brooklyn.util.collections.MutableList;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

/**
 * Convenience methods for working with entity adjunts.
 */
public class EntityAdjuncts {

    public static <T extends EntityAdjunct> T findWithUniqueTag(Iterable<T> adjuncts, Object tag) {
        Preconditions.checkNotNull(tag, "tag");
        for (T adjunct: adjuncts)
            if (tag.equals(adjunct.getUniqueTag())) 
                return adjunct;
        return null;
    }
    
    public static final List<String> SYSTEM_ENRICHER_UNIQUE_TAGS = ImmutableList.of(
        ServiceNotUpLogic.DEFAULT_ENRICHER_UNIQUE_TAG,
        ComputeServiceState.DEFAULT_ENRICHER_UNIQUE_TAG,
        ComputeServiceIndicatorsFromChildrenAndMembers.DEFAULT_UNIQUE_TAG,
        ComputeServiceIndicatorsFromChildrenAndMembers.DEFAULT_UNIQUE_TAG_UP);
    
    public static List<Enricher> getNonSystemEnrichers(Entity entity) {
        List<Enricher> result = MutableList.copyOf(entity.getEnrichers());
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
