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
package brooklyn.entity.nosql.couchbase;

import static brooklyn.util.JavaGroovyEquivalents.groovyTruth;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.render.RendererHints;
import brooklyn.enricher.Enrichers;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.entity.group.AbstractMembershipTrackingPolicy;
import brooklyn.entity.group.DynamicClusterImpl;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.trait.Startable;
import brooklyn.event.AttributeSensor;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.location.Location;
import brooklyn.policy.PolicySpec;
import brooklyn.util.collections.MutableSet;
import brooklyn.util.task.Tasks;
import brooklyn.util.text.ByteSizeStrings;
import brooklyn.util.time.Time;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

public class CouchbaseClusterImpl extends DynamicClusterImpl implements CouchbaseCluster {
    private static final Logger log = LoggerFactory.getLogger(CouchbaseClusterImpl.class);
    private final Object mutex = new Object[0];

    public void init() {
        log.info("Initializing the Couchbase cluster...");
        super.init();
        
        addEnricher(
            Enrichers.builder()
                .transforming(COUCHBASE_CLUSTER_UP_NODES)
                .from(this)
                .publishing(COUCHBASE_CLUSTER_UP_NODE_ADDRESSES)
                .computing(new Function<Set<Entity>, List<String>>() {
                    @Override public List<String> apply(Set<Entity> input) {
                        List<String> addresses = Lists.newArrayList();
                        for (Entity entity : input) {
                            addresses.add(String.format("%s:%s", entity.getAttribute(Attributes.ADDRESS), 
                                    entity.getAttribute(CouchbaseNode.COUCHBASE_WEB_ADMIN_PORT)));
                        }
                        return addresses;
                }
            }).build()
        );
        
        Map<? extends AttributeSensor<? extends Number>, ? extends AttributeSensor<? extends Number>> enricherSetup = 
            ImmutableMap.<AttributeSensor<? extends Number>, AttributeSensor<? extends Number>>builder()
                .put(CouchbaseNode.OPS, CouchbaseCluster.OPS_PER_NODE)
                .put(CouchbaseNode.COUCH_DOCS_DATA_SIZE, CouchbaseCluster.COUCH_DOCS_DATA_SIZE_PER_NODE)
                .put(CouchbaseNode.COUCH_DOCS_ACTUAL_DISK_SIZE, CouchbaseCluster.COUCH_DOCS_ACTUAL_DISK_SIZE_PER_NODE)
                .put(CouchbaseNode.EP_BG_FETCHED, CouchbaseCluster.EP_BG_FETCHED_PER_NODE)
                .put(CouchbaseNode.MEM_USED, CouchbaseCluster.MEM_USED_PER_NODE)
                .put(CouchbaseNode.COUCH_VIEWS_ACTUAL_DISK_SIZE, CouchbaseCluster.COUCH_VIEWS_ACTUAL_DISK_SIZE_PER_NODE)
                .put(CouchbaseNode.CURR_ITEMS, CouchbaseCluster.CURR_ITEMS_PER_NODE)
                .put(CouchbaseNode.VB_REPLICA_CURR_ITEMS, CouchbaseCluster.VB_REPLICA_CURR_ITEMS_PER_NODE)
                .put(CouchbaseNode.COUCH_VIEWS_DATA_SIZE, CouchbaseCluster.COUCH_VIEWS_DATA_SIZE_PER_NODE)
                .put(CouchbaseNode.GET_HITS, CouchbaseCluster.GET_HITS_PER_NODE)
                .put(CouchbaseNode.CMD_GET, CouchbaseCluster.CMD_GET_PER_NODE)
                .put(CouchbaseNode.CURR_ITEMS_TOT, CouchbaseCluster.CURR_ITEMS_TOT_PER_NODE)
            .build();
        
        for (AttributeSensor<? extends Number> nodeSensor : enricherSetup.keySet()) {
            addSummingMemberEnricher(nodeSensor);
            addAveragingMemberEnricher(nodeSensor, enricherSetup.get(nodeSensor));
        }
        
    }
    
    private void addAveragingMemberEnricher(AttributeSensor<? extends Number> fromSensor, AttributeSensor<? extends Number> toSensor) {
        addEnricher(Enrichers.builder()
            .aggregating(fromSensor)
            .publishing(toSensor)
            .fromMembers()
            .computingAverage()
            .build()
        );
    }

    private void addSummingMemberEnricher(AttributeSensor<? extends Number> source) {
        addEnricher(Enrichers.builder()
            .aggregating(source)
            .publishing(source)
            .fromMembers()
            .computingSum()
            .build()
        );
    }

    @Override
    public void start(Collection<? extends Location> locations) {
        super.start(locations);

        connectSensors();
        connectEnrichers();

        //start timeout before adding the servers
        Time.sleep(getConfig(SERVICE_UP_TIME_OUT));

        Optional<Set<Entity>> upNodes = Optional.<Set<Entity>>fromNullable(getAttribute(COUCHBASE_CLUSTER_UP_NODES));
        if (upNodes.isPresent() && !upNodes.get().isEmpty()) {

            //TODO: select a new primary node if this one fails
            Entity primaryNode = upNodes.get().iterator().next();
            ((EntityInternal) primaryNode).setAttribute(CouchbaseNode.IS_PRIMARY_NODE, true);
            setAttribute(COUCHBASE_PRIMARY_NODE, primaryNode);

            Set<Entity> serversToAdd = MutableSet.<Entity>copyOf(getUpNodes());
            serversToAdd.remove(getPrimaryNode());

            if (getUpNodes().size() >= getQuorumSize() && getUpNodes().size() > 1) {
                log.info("number of SERVICE_UP nodes:{} in cluster:{} did reached Quorum:{}, adding the servers", new Object[]{getUpNodes().size(), getId(), getQuorumSize()});
                addServers(serversToAdd);

                //wait for servers to be added to the couchbase server
                try {
                    Tasks.setBlockingDetails("Delaying before advertising cluster up");
                    Time.sleep(getConfig(DELAY_BEFORE_ADVERTISING_CLUSTER));
                } finally {
                    Tasks.resetBlockingDetails();
                }
                Entities.invokeEffector(this, getPrimaryNode(), CouchbaseNode.REBALANCE);

                setAttribute(IS_CLUSTER_INITIALIZED, true);
            } else {
                //TODO: add a repeater to wait for a quorum of servers to be up.
                //retry waiting for service up?
                //check Repeater.
            }
        } else {
            setAttribute(SERVICE_STATE, Lifecycle.ON_FIRE);
        }

    }

    @Override
    public void stop() {
        super.stop();
    }

    public void connectEnrichers() {

        subscribeToMembers(this, SERVICE_UP, new SensorEventListener<Boolean>() {
            @Override
            public void onEvent(SensorEvent<Boolean> event) {
                setAttribute(SERVICE_UP, calculateServiceUp());
            }
        });
    }

    protected void connectSensors() {
        addPolicy(PolicySpec.create(MemberTrackingPolicy.class)
                .displayName("Controller targets tracker")
                .configure("group", this));
    }

    public static class MemberTrackingPolicy extends AbstractMembershipTrackingPolicy {
        @Override protected void onEntityChange(Entity member) {
            ((CouchbaseClusterImpl)entity).onServerPoolMemberChanged(member);
        }

        @Override protected void onEntityAdded(Entity member) {
            ((CouchbaseClusterImpl)entity).onServerPoolMemberChanged(member);
        }

        @Override protected void onEntityRemoved(Entity member) {
            ((CouchbaseClusterImpl)entity).onServerPoolMemberChanged(member);
        }
    };

    protected synchronized void onServerPoolMemberChanged(Entity member) {
        if (log.isTraceEnabled()) log.trace("For {}, considering membership of {} which is in locations {}",
                new Object[]{this, member, member.getLocations()});

        //FIXME: make use of servers to be added after cluster initialization.
        synchronized (mutex) {
            if (belongsInServerPool(member)) {

                Optional<Set<Entity>> upNodes = Optional.fromNullable(getUpNodes());
                if (upNodes.isPresent()) {

                    if (!upNodes.get().contains(member)) {
                        Set<Entity> newNodes = Sets.newHashSet(getUpNodes());
                        newNodes.add(member);
                        setAttribute(COUCHBASE_CLUSTER_UP_NODES, newNodes);

                        //add to set of servers to be added.
                        if (isClusterInitialized()) {
                            addServer(member);
                        }
                    }
                } else {
                    Set<Entity> newNodes = Sets.newHashSet();
                    newNodes.add(member);
                    setAttribute(COUCHBASE_CLUSTER_UP_NODES, newNodes);

                    if (isClusterInitialized()) {
                        addServer(member);
                    }
                }
            } else {
                Set<Entity> upNodes = getUpNodes();
                if (upNodes != null && upNodes.contains(member)) {
                    upNodes.remove(member);
                    setAttribute(COUCHBASE_CLUSTER_UP_NODES, upNodes);
                    log.info("Removing couchbase node {}: {}; from cluster", new Object[]{this, member});
                }
            }
            if (log.isTraceEnabled()) log.trace("Done {} checkEntity {}", this, member);
        }
    }

    protected boolean belongsInServerPool(Entity member) {
        if (!groovyTruth(member.getAttribute(Startable.SERVICE_UP))) {
            if (log.isTraceEnabled()) log.trace("Members of {}, checking {}, eliminating because not up", this, member);
            return false;
        }
        if (!getMembers().contains(member)) {
            if (log.isTraceEnabled())
                log.trace("Members of {}, checking {}, eliminating because not member", this, member);

            return false;
        }
        if (log.isTraceEnabled()) log.trace("Members of {}, checking {}, approving", this, member);

        return true;
    }


    protected EntitySpec<?> getMemberSpec() {
        EntitySpec<?> result = super.getMemberSpec();
        if (result != null) return result;
        return EntitySpec.create(CouchbaseNode.class);
    }


    protected int getQuorumSize() {
        Integer quorumSize = getConfig(CouchbaseCluster.INITIAL_QUORUM_SIZE);
        if (quorumSize != null && quorumSize > 0)
            return quorumSize;
        // by default the quorum would be floor(initial_cluster_size/2) + 1
        return (int) Math.floor(getConfig(INITIAL_SIZE) / 2) + 1;
    }

    protected int getActualSize() {
        return Optional.fromNullable(getAttribute(CouchbaseCluster.ACTUAL_CLUSTER_SIZE)).or(-1);
    }

    private Set<Entity> getUpNodes() {
        return getAttribute(COUCHBASE_CLUSTER_UP_NODES);
    }

    private Entity getPrimaryNode() {
        return getAttribute(COUCHBASE_PRIMARY_NODE);
    }

    @Override
    protected boolean calculateServiceUp() {
        if (!super.calculateServiceUp()) return false;
        Set<Entity> upNodes = getAttribute(COUCHBASE_CLUSTER_UP_NODES);
        if (upNodes == null || upNodes.isEmpty() || upNodes.size() < getQuorumSize()) return false;
        return true;
    }

    protected void addServers(Set<Entity> serversToAdd) {
        Preconditions.checkNotNull(serversToAdd);
        for (Entity e : serversToAdd) {
            if (!isMemberInCluster(e)) {
                addServer(e);
            }
        }
    }

    protected void addServer(Entity serverToAdd) {
        Preconditions.checkNotNull(serverToAdd);
        if (!isMemberInCluster(serverToAdd)) {
            String hostname = serverToAdd.getAttribute(Attributes.HOSTNAME) + ":" + serverToAdd.getConfig(CouchbaseNode.COUCHBASE_WEB_ADMIN_PORT).iterator().next();
            String username = serverToAdd.getConfig(CouchbaseNode.COUCHBASE_ADMIN_USERNAME);
            String password = serverToAdd.getConfig(CouchbaseNode.COUCHBASE_ADMIN_PASSWORD);

            if (isClusterInitialized()) {
                Entities.invokeEffectorWithArgs(this, getPrimaryNode(), CouchbaseNode.SERVER_ADD_AND_REBALANCE, hostname, username, password);
            } else {
                Entities.invokeEffectorWithArgs(this, getPrimaryNode(), CouchbaseNode.SERVER_ADD, hostname, username, password);
            }
            //FIXME check feedback of whether the server was added.
            ((EntityInternal) serverToAdd).setAttribute(CouchbaseNode.IS_IN_CLUSTER, true);
        }
    }

    public boolean isClusterInitialized() {
        return Optional.fromNullable(getAttribute(IS_CLUSTER_INITIALIZED)).or(false);
    }

    public boolean isMemberInCluster(Entity e) {
        return Optional.fromNullable(e.getAttribute(CouchbaseNode.IS_IN_CLUSTER)).or(false);
    }
    
    static {
        RendererHints.register(COUCH_DOCS_DATA_SIZE_PER_NODE, RendererHints.displayValue(ByteSizeStrings.metric()));
        RendererHints.register(COUCH_DOCS_ACTUAL_DISK_SIZE_PER_NODE, RendererHints.displayValue(ByteSizeStrings.metric()));
        RendererHints.register(MEM_USED_PER_NODE, RendererHints.displayValue(ByteSizeStrings.metric()));
        RendererHints.register(COUCH_VIEWS_ACTUAL_DISK_SIZE_PER_NODE, RendererHints.displayValue(ByteSizeStrings.metric()));
        RendererHints.register(COUCH_VIEWS_DATA_SIZE_PER_NODE, RendererHints.displayValue(ByteSizeStrings.metric()));
    }
}
