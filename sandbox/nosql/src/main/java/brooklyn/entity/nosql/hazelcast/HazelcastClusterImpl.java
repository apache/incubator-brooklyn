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
package brooklyn.entity.nosql.hazelcast;

import java.util.concurrent.atomic.AtomicInteger;

import brooklyn.entity.group.DynamicClusterImpl;
import brooklyn.entity.proxying.EntitySpec;

public class HazelcastClusterImpl extends DynamicClusterImpl implements HazelcastCluster {
    
    private AtomicInteger nextMemberId = new AtomicInteger(0);

    @Override
    protected EntitySpec<?> getMemberSpec() {
        EntitySpec<?> spec = EntitySpec.create(getConfig(MEMBER_SPEC, EntitySpec.create(HazelcastNode.class)));
        
        spec.configure(HazelcastNode.CLUSTER_NAME, getConfig(HazelcastClusterImpl.CLUSTER_NAME))
            .configure(HazelcastNode.NODE_NAME, "hazelcast-" + nextMemberId.incrementAndGet());
        
        return spec;
    }
    
    @Override
    public String getClusterName() {
        return getConfig(CLUSTER_NAME);
    }
    
}
