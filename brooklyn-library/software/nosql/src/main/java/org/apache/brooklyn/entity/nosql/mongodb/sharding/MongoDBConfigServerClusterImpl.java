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
package org.apache.brooklyn.entity.nosql.mongodb.sharding;

import java.util.Collection;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.core.location.access.BrooklynAccessUtils;
import org.apache.brooklyn.entity.group.DynamicClusterImpl;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.net.HostAndPort;

public class MongoDBConfigServerClusterImpl extends DynamicClusterImpl implements MongoDBConfigServerCluster {
    
    @Override
    protected EntitySpec<?> getMemberSpec() {
        if (super.getMemberSpec() != null)
            return super.getMemberSpec();
        return EntitySpec.create(MongoDBConfigServer.class);
    }
    
    @Override
    public void start(Collection<? extends Location> locs) {
        super.start(locs);
        
        // TODO this should be an enricher
        Iterable<String> memberHostNamesAndPorts = Iterables.transform(getMembers(), new Function<Entity, String>() {
            @Override
            public String apply(Entity entity) {
                HostAndPort hostAndPort = BrooklynAccessUtils.getBrooklynAccessibleAddress(entity, entity.getAttribute(MongoDBConfigServer.PORT));
                return hostAndPort.getHostText() + ":" + hostAndPort.getPort();
            }
        });
        sensors().set(MongoDBConfigServerCluster.CONFIG_SERVER_ADDRESSES, ImmutableList.copyOf(memberHostNamesAndPorts));
    }

}
