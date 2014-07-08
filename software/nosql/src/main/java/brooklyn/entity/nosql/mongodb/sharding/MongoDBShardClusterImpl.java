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

import java.net.UnknownHostException;
import java.util.Collection;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.group.DynamicClusterImpl;
import brooklyn.entity.nosql.mongodb.MongoDBClientSupport;
import brooklyn.entity.nosql.mongodb.MongoDBReplicaSet;
import brooklyn.entity.nosql.mongodb.MongoDBServer;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.trait.Startable;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.location.Location;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.text.Strings;

import com.google.common.collect.Sets;

public class MongoDBShardClusterImpl extends DynamicClusterImpl implements MongoDBShardCluster {

    private static final Logger LOG = LoggerFactory.getLogger(MongoDBShardClusterImpl.class);
    // TODO: Need to use attributes for this in order to support brooklyn restart 
    private Set<Entity> addedMembers = Sets.newConcurrentHashSet();

    @Override
    protected EntitySpec<?> getMemberSpec() {
        EntitySpec<?> result = super.getMemberSpec();
        if (result == null)
            result = EntitySpec.create(MongoDBReplicaSet.class);
        result.configure(DynamicClusterImpl.INITIAL_SIZE, getConfig(MongoDBShardedDeployment.SHARD_REPLICASET_SIZE));
        return result;
    }

    @Override
    public void start(Collection<? extends Location> locations) {
        subscribeToMembers(this, Startable.SERVICE_UP, new SensorEventListener<Boolean>() {
            public void onEvent(SensorEvent<Boolean> event) {
                addShards();
            }
        });

        super.start(locations);
        
        MongoDBRouterCluster routers = getParent().getAttribute(MongoDBShardedDeployment.ROUTER_CLUSTER);
        subscribe(routers, MongoDBRouterCluster.ANY_RUNNING_ROUTER, new SensorEventListener<MongoDBRouter>() {
            public void onEvent(SensorEvent<MongoDBRouter> event) {
                if (event.getValue() != null)
                    addShards();
            }
        });
        
    }

    protected void addShards() {
        MongoDBRouter router = getParent().getAttribute(MongoDBShardedDeployment.ROUTER_CLUSTER).getAttribute(MongoDBRouterCluster.ANY_RUNNING_ROUTER);
        if (router == null)
            return;
        
        MongoDBClientSupport client;
        try {
            client = MongoDBClientSupport.forServer(router);
        } catch (UnknownHostException e) {
            throw Exceptions.propagate(e);
        }
        for (Entity member : this.getMembers()) {
            if (member.getAttribute(Startable.SERVICE_UP) && !addedMembers.contains(member)) {
                MongoDBServer primary = member.getAttribute(MongoDBReplicaSet.PRIMARY_ENTITY);
                String addr = Strings.removeFromStart(primary.getAttribute(MongoDBServer.MONGO_SERVER_ENDPOINT), "http://");
                String replicaSetURL = ((MongoDBReplicaSet) member).getName() + "/" + addr;
                LOG.info("Using {} to add shard URL {}...", router, replicaSetURL);
                client.addShardToRouter(replicaSetURL);
                addedMembers.add(member);
            }
        }
    }
}
