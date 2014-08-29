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
package brooklyn.entity.nosql.riak;

import static brooklyn.util.JavaGroovyEquivalents.groovyTruth;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.entity.basic.ServiceStateLogic;
import brooklyn.entity.basic.ServiceStateLogic.ServiceNotUpLogic;
import brooklyn.entity.group.AbstractMembershipTrackingPolicy;
import brooklyn.entity.group.DynamicClusterImpl;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.trait.Startable;
import brooklyn.location.Location;
import brooklyn.policy.PolicySpec;
import brooklyn.util.time.Time;


public class RiakClusterImpl extends DynamicClusterImpl implements RiakCluster {
    private static final Logger log = LoggerFactory.getLogger(RiakClusterImpl.class);
    private AtomicBoolean isFirstNodeSet = new AtomicBoolean();

    public void init() {
        super.init();
        log.info("Initializing the riak cluster...");
        setAttribute(IS_CLUSTER_INIT, false);
    }

    @Override
    public void start(Collection<? extends Location> locations) {
        super.start(locations);
        connectSensors();

        Time.sleep(getConfig(DELAY_BEFORE_ADVERTISING_CLUSTER));

        //FIXME: add a quorum to tolerate failed nodes before setting on fire.
        Optional<Entity> anyNode = Iterables.tryFind(getMembers(), new Predicate<Entity>() {

            @Override
            public boolean apply(@Nullable Entity entity) {
                return (entity instanceof RiakNode && hasMemberJoinedCluster(entity) && entity.getAttribute(RiakNode.SERVICE_UP));
            }
        });

        if (anyNode.isPresent()) {
            log.info("Planning and Committing cluster changes on node: {}, cluster: {}", anyNode.get().getId(), getId());
            Entities.invokeEffector(this, anyNode.get(), RiakNode.COMMIT_RIAK_CLUSTER);
            setAttribute(IS_CLUSTER_INIT, true);
        } else {
            log.warn("No Riak Nodes are found on the cluster: {}. Initialization Failed", getId());
            ServiceStateLogic.setExpectedState(this, Lifecycle.ON_FIRE);
        }
    }

    protected EntitySpec<?> getMemberSpec() {
        return getConfig(MEMBER_SPEC, EntitySpec.create(RiakNode.class));

    }

    protected void connectSensors() {
        addPolicy(PolicySpec.create(MemberTrackingPolicy.class)
                .displayName("Controller targets tracker")
                .configure("sensorsToTrack", ImmutableSet.of(RiakNode.SERVICE_UP))
                .configure("group", this));
    }

    protected synchronized void onServerPoolMemberChanged(Entity member) {
        if (log.isTraceEnabled()) log.trace("For {}, considering membership of {} which is in locations {}",
                new Object[]{this, member, member.getLocations()});

        Map<Entity, String> nodes = getAttribute(RIAK_CLUSTER_NODES);
        if (belongsInServerPool(member)) {
            // TODO can we discover the nodes by asking the riak cluster, rather than assuming what we add will be in there?
            // TODO and can we do join as part of node starting?

            if (nodes == null) nodes = Maps.newLinkedHashMap();
            String riakName = getRiakName(member);

            if (riakName == null) {
                log.error("Unable to get riak name for node: {}", member.getId());
            } else {
                //flag a first node to be the first node in the riak cluster.
                if (!isFirstNodeSet.get()) {
                    nodes.put(member, riakName);
                    setAttribute(RIAK_CLUSTER_NODES, nodes);

                    ((EntityInternal) member).setAttribute(RiakNode.RIAK_NODE_HAS_JOINED_CLUSTER, Boolean.TRUE);
                    isFirstNodeSet.set(true);

                    log.info("Adding riak node {}: {}; {} to cluster", new Object[]{this, member, getRiakName(member)});

                } else {

                    //TODO: be wary of erreneous nodes but are still flagged 'in cluster'
                    //add the new node to be part of the riak cluster.
                    Optional<Entity> anyNodeInCluster = Iterables.tryFind(nodes.keySet(), new Predicate<Entity>() {
                        @Override
                        public boolean apply(@Nullable Entity node) {
                            return (node instanceof RiakNode && hasMemberJoinedCluster(node));
                        }
                    });

                    if (anyNodeInCluster.isPresent()) {
                        if (!nodes.containsKey(member) && !hasMemberJoinedCluster(member)) {


                            String anyNodeName = anyNodeInCluster.get().getAttribute(RiakNode.RIAK_NODE_NAME);
                            Entities.invokeEffectorWithArgs(this, member, RiakNode.JOIN_RIAK_CLUSTER, anyNodeName);
                            if (getAttribute(IS_CLUSTER_INIT)) {
                                Entities.invokeEffector(RiakClusterImpl.this, anyNodeInCluster.get(), RiakNode.COMMIT_RIAK_CLUSTER);
                            }
                            nodes.put(member, riakName);
                            setAttribute(RIAK_CLUSTER_NODES, nodes);
                            log.info("Adding riak node {}: {}; {} to cluster", new Object[]{this, member, getRiakName(member)});
                        }
                    } else {
                        log.error("entity {}: is not present", member.getId());
                    }
                }
            }
        } else {
            if (nodes != null && nodes.containsKey(member)) {
                final Entity memberToBeRemoved = member;

                Optional<Entity> anyNodeInCluster = Iterables.tryFind(nodes.keySet(), new Predicate<Entity>() {

                    @Override
                    public boolean apply(@Nullable Entity node) {
                        return (node instanceof RiakNode && hasMemberJoinedCluster(node) && !node.equals(memberToBeRemoved));
                    }
                });
                if (anyNodeInCluster.isPresent()) {
                    Entities.invokeEffectorWithArgs(this, anyNodeInCluster.get(), RiakNode.LEAVE_RIAK_CLUSTER, getRiakName(memberToBeRemoved));
                }

                nodes.remove(member);
                setAttribute(RIAK_CLUSTER_NODES, nodes);
                log.info("Removing riak node {}: {}; {} from cluster", new Object[]{this, member, getRiakName(member)});

            }
        }
        
        ServiceNotUpLogic.updateNotUpIndicatorRequiringNonEmptyMap(this, RIAK_CLUSTER_NODES);
        if (log.isTraceEnabled()) log.trace("Done {} checkEntity {}", this, member);
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

    private String getRiakName(Entity node) {
        return node.getAttribute(RiakNode.RIAK_NODE_NAME);
    }

    private boolean hasMemberJoinedCluster(Entity member) {
        return ((RiakNode) member).hasJoinedCluster();
    }

    public static class MemberTrackingPolicy extends AbstractMembershipTrackingPolicy {
        @Override
        protected void onEntityEvent(EventType type, Entity entity) {
            ((RiakClusterImpl) super.entity).onServerPoolMemberChanged(entity);
        }
    }
}
