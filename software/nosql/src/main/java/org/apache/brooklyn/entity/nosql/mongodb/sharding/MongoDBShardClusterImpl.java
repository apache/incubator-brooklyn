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

import java.net.UnknownHostException;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.sensor.SensorEvent;
import org.apache.brooklyn.api.sensor.SensorEventListener;
import org.apache.brooklyn.core.entity.trait.Startable;
import org.apache.brooklyn.entity.group.DynamicClusterImpl;
import org.apache.brooklyn.entity.nosql.mongodb.MongoDBClientSupport;
import org.apache.brooklyn.entity.nosql.mongodb.MongoDBReplicaSet;
import org.apache.brooklyn.entity.nosql.mongodb.MongoDBServer;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.time.Duration;
import org.apache.brooklyn.util.time.Time;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Sets;

public class MongoDBShardClusterImpl extends DynamicClusterImpl implements MongoDBShardCluster {

    private static final Logger LOG = LoggerFactory.getLogger(MongoDBShardClusterImpl.class);
    
    // TODO: Need to use attributes for this in order to support brooklyn restart 
    private Set<Entity> addedMembers = Sets.newConcurrentHashSet();

    // TODO: Need to use attributes for this in order to support brooklyn restart 
    private Set<Entity> addingMembers = Sets.newConcurrentHashSet();

    /**
     * For shard addition and removal.
     * Used for retrying.
     * 
     * TODO Should use ExecutionManager.
     */
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

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

    @Override
    public void stop() {
        // TODO Note that after this the executor will not run if the set is restarted.
        executor.shutdownNow();
        super.stop();
    }
    
    @Override
    public void onManagementStopped() {
        super.onManagementStopped();
        executor.shutdownNow();
    }

    protected void addShards() {
        MongoDBRouter router = getParent().getAttribute(MongoDBShardedDeployment.ROUTER_CLUSTER).getAttribute(MongoDBRouterCluster.ANY_RUNNING_ROUTER);
        if (router == null) {
            if (LOG.isTraceEnabled()) LOG.trace("Not adding shards because no running router in {}", this);
            return;
        }
        
        for (Entity member : this.getMembers()) {
            if (member.getAttribute(Startable.SERVICE_UP) && !addingMembers.contains(member)) {
                LOG.info("{} adding shard {}", new Object[] {MongoDBShardClusterImpl.this, member});
                addingMembers.add(member);
                addShardAsync(member);
            }
        }
    }
    
    protected void addShardAsync(final Entity replicaSet) {
        final Duration timeout = Duration.minutes(20);
        final Stopwatch stopwatch = Stopwatch.createStarted();
        final AtomicInteger attempts = new AtomicInteger();
        
        // TODO Don't use executor, use ExecutionManager; but following pattern in MongoDBReplicaSetImpl for now.
        executor.submit(new Runnable() {
            @Override
            public void run() {
                boolean reschedule;
                MongoDBRouter router = getParent().getAttribute(MongoDBShardedDeployment.ROUTER_CLUSTER).getAttribute(MongoDBRouterCluster.ANY_RUNNING_ROUTER);
                if (router == null) {
                    LOG.debug("Rescheduling adding shard {} because no running router for cluster {}", replicaSet, this);
                    reschedule = true;
                } else {
                    MongoDBClientSupport client;
                    try {
                        client = MongoDBClientSupport.forServer(router);
                    } catch (UnknownHostException e) {
                        throw Exceptions.propagate(e);
                    }
                    
                    MongoDBServer primary = replicaSet.getAttribute(MongoDBReplicaSet.PRIMARY_ENTITY);
                    if (primary != null) {
                        String addr = String.format("%s:%d", primary.getAttribute(MongoDBServer.SUBNET_HOSTNAME), primary.getAttribute(MongoDBServer.PORT));
                        String replicaSetURL = ((MongoDBReplicaSet) replicaSet).getName() + "/" + addr;
                        boolean added = client.addShardToRouter(replicaSetURL);
                        if (added) {
                            LOG.info("{} added shard {} via {}", new Object[] {MongoDBShardClusterImpl.this, replicaSetURL, router});
                            addedMembers.add(replicaSet);
                            reschedule = false;
                        } else {
                            LOG.debug("Rescheduling addition of shard {} because add failed via router {}", replicaSetURL, router);
                            reschedule = true;
                        }
                    } else {
                        LOG.debug("Rescheduling addition of shard {} because primary is null", replicaSet);
                        reschedule = true;
                    }
                }
                
                if (reschedule) {
                    int numAttempts = attempts.incrementAndGet();
                    if (numAttempts > 1 && timeout.toMilliseconds() > stopwatch.elapsed(TimeUnit.MILLISECONDS)) {
                        executor.schedule(this, 3, TimeUnit.SECONDS);
                    } else {
                        LOG.warn("Timeout after {} attempts ({}) adding shard {}; aborting", 
                                new Object[] {numAttempts, Time.makeTimeStringRounded(stopwatch), replicaSet});
                        addingMembers.remove(replicaSet);
                    }
                }
            }
        });
    }
}
