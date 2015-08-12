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
package org.apache.brooklyn.entity.nosql.riak;

import static brooklyn.util.JavaGroovyEquivalents.groovyTruth;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.apache.brooklyn.policy.EnricherSpec;
import org.apache.brooklyn.policy.PolicySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.enricher.Enrichers;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.basic.EntityPredicates;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.entity.basic.ServiceStateLogic;
import brooklyn.entity.basic.ServiceStateLogic.ServiceNotUpLogic;
import brooklyn.entity.group.AbstractMembershipTrackingPolicy;
import brooklyn.entity.group.DynamicClusterImpl;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.trait.Startable;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.DependentConfiguration;
import brooklyn.util.task.Tasks;
import brooklyn.util.time.Duration;
import brooklyn.util.time.Time;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class RiakClusterImpl extends DynamicClusterImpl implements RiakCluster {

    private static final Logger log = LoggerFactory.getLogger(RiakClusterImpl.class);

    private transient Object mutex = new Object[0];

    public void init() {
        super.init();
        log.info("Initializing the riak cluster...");
        setAttribute(IS_CLUSTER_INIT, false);
    }

    @Override
    protected void doStart() {
        super.doStart();
        connectSensors();

        try {
            Duration delay = getConfig(DELAY_BEFORE_ADVERTISING_CLUSTER);
            Tasks.setBlockingDetails("Sleeping for "+delay+" before advertising cluster available");
            Time.sleep(delay);
        } finally {
            Tasks.resetBlockingDetails();
        }

        //FIXME: add a quorum to tolerate failed nodes before setting on fire.
        @SuppressWarnings("unchecked")
        Optional<Entity> anyNode = Iterables.tryFind(getMembers(), Predicates.and(
                Predicates.instanceOf(RiakNode.class),
                EntityPredicates.attributeEqualTo(RiakNode.RIAK_NODE_HAS_JOINED_CLUSTER, true),
                EntityPredicates.attributeEqualTo(RiakNode.SERVICE_UP, true)));
        if (anyNode.isPresent()) {
            setAttribute(IS_CLUSTER_INIT, true);
        } else {
            log.warn("No Riak Nodes are found on the cluster: {}. Initialization Failed", getId());
            ServiceStateLogic.setExpectedState(this, Lifecycle.ON_FIRE);
        }
    }

    protected EntitySpec<?> getMemberSpec() {
        EntitySpec<?> result = config().get(MEMBER_SPEC);
        if (result!=null) return result;
        return EntitySpec.create(RiakNode.class);
    }

    protected void connectSensors() {
        addPolicy(PolicySpec.create(MemberTrackingPolicy.class)
                .displayName("Controller targets tracker")
                .configure("sensorsToTrack", ImmutableSet.of(RiakNode.SERVICE_UP))
                .configure("group", this));

        EnricherSpec<?> first = Enrichers.builder()
                 .aggregating(Attributes.MAIN_URI)
                 .publishing(Attributes.MAIN_URI)
                 .computing(new Function<Collection<URI>,URI>() {
                    @Override
                    public URI apply(Collection<URI> input) {
                        return input.iterator().next();
                    } })
                 .fromMembers()
                 .build();
        addEnricher(first);
        
        Map<? extends AttributeSensor<? extends Number>, ? extends AttributeSensor<? extends Number>> enricherSetup = 
            ImmutableMap.<AttributeSensor<? extends Number>, AttributeSensor<? extends Number>>builder()
                .put(RiakNode.NODE_PUTS, RiakCluster.NODE_PUTS_1MIN_PER_NODE)
                .put(RiakNode.NODE_GETS, RiakCluster.NODE_GETS_1MIN_PER_NODE)
                .put(RiakNode.NODE_OPS, RiakCluster.NODE_OPS_1MIN_PER_NODE)
            .build();
        // construct sum and average over cluster
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

    protected void onServerPoolMemberChanged(final Entity member) {
        synchronized (mutex) {
            log.trace("For {}, considering membership of {} which is in locations {}", new Object[]{ this, member, member.getLocations() });

            Map<Entity, String> nodes = getAttribute(RIAK_CLUSTER_NODES);
            if (belongsInServerPool(member)) {
                // TODO can we discover the nodes by asking the riak cluster, rather than assuming what we add will be in there?
                // TODO and can we do join as part of node starting?

                if (nodes == null) {
                    nodes = Maps.newLinkedHashMap();
                }
                String riakName = getRiakName(member);
                Preconditions.checkNotNull(riakName);

                // flag a first node to be the first node in the riak cluster.
                Boolean firstNode = getAttribute(IS_FIRST_NODE_SET);
                if (!Boolean.TRUE.equals(firstNode)) {
                    setAttribute(IS_FIRST_NODE_SET, Boolean.TRUE);

                    nodes.put(member, riakName);
                    setAttribute(RIAK_CLUSTER_NODES, nodes);

                    ((EntityInternal) member).setAttribute(RiakNode.RIAK_NODE_HAS_JOINED_CLUSTER, Boolean.TRUE);

                    log.info("Added initial Riak node {}: {}; {} to new cluster", new Object[] { this, member, getRiakName(member) });
                } else {
                    // TODO: be wary of erroneous nodes but are still flagged 'in cluster'
                    // add the new node to be part of the riak cluster.
                    Optional<Entity> anyNodeInCluster = Iterables.tryFind(nodes.keySet(), Predicates.and(
                            Predicates.instanceOf(RiakNode.class),
                            EntityPredicates.attributeEqualTo(RiakNode.RIAK_NODE_HAS_JOINED_CLUSTER, true)));
                    if (anyNodeInCluster.isPresent()) {
                        if (!nodes.containsKey(member) && member.getAttribute(RiakNode.RIAK_NODE_HAS_JOINED_CLUSTER) == null) {
                            String anyNodeName = anyNodeInCluster.get().getAttribute(RiakNode.RIAK_NODE_NAME);
                            Entities.invokeEffectorWithArgs(this, member, RiakNode.JOIN_RIAK_CLUSTER, anyNodeName).blockUntilEnded();
                            nodes.put(member, riakName);
                            setAttribute(RIAK_CLUSTER_NODES, nodes);
                            log.info("Added Riak node {}: {}; {} to cluster", new Object[] { this, member, getRiakName(member) });
                        }
                    } else {
                        log.error("isFirstNodeSet, but no cluster members found to add {}", member.getId());
                    }
                }
            } else {
                if (nodes != null && nodes.containsKey(member)) {
                    DependentConfiguration.attributeWhenReady(member, RiakNode.RIAK_NODE_HAS_JOINED_CLUSTER, Predicates.equalTo(false)).blockUntilEnded(Duration.TWO_MINUTES);
                    @SuppressWarnings("unchecked")
                    Optional<Entity> anyNodeInCluster = Iterables.tryFind(nodes.keySet(), Predicates.and(
                            Predicates.instanceOf(RiakNode.class),
                            EntityPredicates.attributeEqualTo(RiakNode.RIAK_NODE_HAS_JOINED_CLUSTER, true),
                            Predicates.not(Predicates.equalTo(member))));
                    if (anyNodeInCluster.isPresent()) {
                        Entities.invokeEffectorWithArgs(this, anyNodeInCluster.get(), RiakNode.REMOVE_FROM_CLUSTER, getRiakName(member)).blockUntilEnded();
                    }
                    nodes.remove(member);
                    setAttribute(RIAK_CLUSTER_NODES, nodes);
                    log.info("Removed Riak node {}: {}; {} from cluster", new Object[]{ this, member, getRiakName(member) });
                }
            }

            ServiceNotUpLogic.updateNotUpIndicatorRequiringNonEmptyMap(this, RIAK_CLUSTER_NODES);

            calculateClusterAddresses();
        }
    }

    private void calculateClusterAddresses() {
        List<String> addresses = Lists.newArrayList();
        List<String> addressesPbPort = Lists.newArrayList();
        for (Entity entity : this.getMembers()) {
            if (entity instanceof RiakNode && entity.getAttribute(Attributes.SERVICE_UP)) {
                RiakNode riakNode = (RiakNode) entity;
                addresses.add(riakNode.getAttribute(Attributes.SUBNET_HOSTNAME) + ":" + riakNode.getAttribute(RiakNode.RIAK_WEB_PORT));
                addressesPbPort.add(riakNode.getAttribute(Attributes.SUBNET_HOSTNAME) + ":" + riakNode.getAttribute(RiakNode.RIAK_PB_PORT));
            }
        }
        setAttribute(RiakCluster.NODE_LIST, Joiner.on(",").join(addresses));
        setAttribute(RiakCluster.NODE_LIST_PB_PORT, Joiner.on(",").join(addressesPbPort));
    }

    protected boolean belongsInServerPool(Entity member) {
        if (!groovyTruth(member.getAttribute(Startable.SERVICE_UP))) {
            log.trace("Members of {}, checking {}, eliminating because not up", this, member);
            return false;
        }
        if (!getMembers().contains(member)) {
            log.trace("Members of {}, checking {}, eliminating because not member", this, member);
            return false;
        }
        log.trace("Members of {}, checking {}, approving", this, member);

        return true;
    }

    private String getRiakName(Entity node) {
        return node.getAttribute(RiakNode.RIAK_NODE_NAME);
    }

    public static class MemberTrackingPolicy extends AbstractMembershipTrackingPolicy {
        @Override
        protected void onEntityEvent(EventType type, Entity entity) {
            ((RiakClusterImpl) super.entity).onServerPoolMemberChanged(entity);
        }
    }
}
