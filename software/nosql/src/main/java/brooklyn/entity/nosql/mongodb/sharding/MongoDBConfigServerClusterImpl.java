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
package brooklyn.entity.nosql.mongodb.sharding;

import java.util.Collection;

import brooklyn.entity.Entity;
import brooklyn.entity.group.DynamicClusterImpl;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.location.Location;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

public class MongoDBConfigServerClusterImpl extends DynamicClusterImpl implements MongoDBConfigServerCluster {
    
    @Override
    protected EntitySpec<?> getMemberSpec() {
        if (super.getMemberSpec() != null)
            return super.getMemberSpec();
        return EntitySpec.create(MongoDBConfigServer.class);
    }
    

    @Override
    protected boolean calculateServiceUp() {
        // Number of config servers is fixed at INITIAL_SIZE
        int requiredMembers = this.getConfig(INITIAL_SIZE);
        int availableMembers = 0;
        for (Entity entity : getMembers()) {
            if (entity instanceof MongoDBConfigServer & entity.getAttribute(SERVICE_UP)) {
                availableMembers++;
            }
        }
        return availableMembers >= requiredMembers;
    }
    
    @Override
    public void start(Collection<? extends Location> locs) {
        super.start(locs);
        Iterable<String> memberHostNamesAndPorts = Iterables.transform(getMembers(), new Function<Entity, String>() {
            @Override
            public String apply(Entity entity) {
                return entity.getAttribute(MongoDBConfigServer.HOSTNAME) + ":" + entity.getAttribute(MongoDBConfigServer.PORT);
            }
        });
        setAttribute(MongoDBConfigServerCluster.CONFIG_SERVER_ADDRESSES, ImmutableList.copyOf(memberHostNamesAndPorts));
    }

}
