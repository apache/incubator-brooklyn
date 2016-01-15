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
package org.apache.brooklyn.entity.nosql.cassandra;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.policy.PolicySpec;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.api.sensor.SensorEvent;
import org.apache.brooklyn.api.sensor.SensorEventListener;
import org.apache.brooklyn.core.effector.EffectorBody;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.EntityPredicates;
import org.apache.brooklyn.core.entity.lifecycle.Lifecycle;
import org.apache.brooklyn.core.entity.lifecycle.ServiceStateLogic.ServiceNotUpLogic;
import org.apache.brooklyn.core.location.Machines;
import org.apache.brooklyn.enricher.stock.Enrichers;
import org.apache.brooklyn.entity.group.AbstractMembershipTrackingPolicy;
import org.apache.brooklyn.entity.group.DynamicClusterImpl;
import org.apache.brooklyn.entity.group.DynamicGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.brooklyn.util.collections.MutableList;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.collections.MutableSet;
import org.apache.brooklyn.util.core.ResourceUtils;
import org.apache.brooklyn.util.core.config.ConfigBag;
import org.apache.brooklyn.util.text.Strings;
import org.apache.brooklyn.util.time.Time;

import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.base.Supplier;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.common.net.HostAndPort;

/**
 * Implementation of {@link CassandraDatacenter}.
 * <p>
 * Several subtleties to note:
 * - a node may take some time after it is running and serving JMX to actually be contactable on its thrift port
 *   (so we wait for thrift port to be contactable)
 * - sometimes new nodes take a while to peer, and/or take a while to get a consistent schema
 *   (each up to 1m; often very close to the 1m) 
 */
public class CassandraDatacenterImpl extends DynamicClusterImpl implements CassandraDatacenter {

    /*
     * TODO Seed management is hard!
     *  - The ServiceRestarter is not doing customize(), so is not refreshing the seeds in cassandra.yaml.
     *    If we have two nodes that were seeds for each other and they both restart at the same time, we'll have a split brain.
     */
    
    private static final Logger log = LoggerFactory.getLogger(CassandraDatacenterImpl.class);

    // Mutex for synchronizing during re-size operations
    private final Object mutex = new Object[0];

    private final Supplier<Set<Entity>> defaultSeedSupplier = new Supplier<Set<Entity>>() {
        // Mutex for (re)calculating our seeds
        // TODO is this very dangerous?! Calling out to SeedTracker, which calls out to alien getAttribute()/getConfig(). But I think that's ok.
        // TODO might not need mutex? previous race was being caused by something else, other than concurrent calls!
        private final Object seedMutex = new Object();
        
        @Override
        public Set<Entity> get() {
            synchronized (seedMutex) {
                boolean hasPublishedSeeds = Boolean.TRUE.equals(getAttribute(HAS_PUBLISHED_SEEDS));
                int quorumSize = getSeedQuorumSize();
                Set<Entity> potentialSeeds = gatherPotentialSeeds();
                Set<Entity> potentialRunningSeeds = gatherPotentialRunningSeeds();
                boolean stillWaitingForQuorum = (!hasPublishedSeeds) && (potentialSeeds.size() < quorumSize);
                
                if (stillWaitingForQuorum) {
                    if (log.isDebugEnabled()) log.debug("Not refreshed seeds of cluster {}, because still waiting for quorum (need {}; have {} potentials)", new Object[] {CassandraDatacenterImpl.class, quorumSize, potentialSeeds.size()});
                    return ImmutableSet.of();
                } else if (hasPublishedSeeds) {
                    Set<Entity> currentSeeds = getAttribute(CURRENT_SEEDS);
                    if (getAttribute(SERVICE_STATE_ACTUAL) == Lifecycle.STARTING) {
                        if (Sets.intersection(currentSeeds, potentialSeeds).isEmpty()) {
                            log.warn("Cluster {} lost all its seeds while starting! Subsequent failure likely, but changing seeds during startup would risk split-brain: seeds={}", new Object[] {CassandraDatacenterImpl.this, currentSeeds});
                        }
                        return currentSeeds;
                    } else if (potentialRunningSeeds.isEmpty()) {
                        // TODO Could be race where nodes have only just returned from start() and are about to 
                        // transition to serviceUp; so don't just abandon all our seeds!
                        log.warn("Cluster {} has no running seeds (yet?); leaving seeds as-is; but risks split-brain if these seeds come back up!", new Object[] {CassandraDatacenterImpl.this});
                        return currentSeeds;
                    } else {
                        Set<Entity> result = trim(quorumSize, potentialRunningSeeds);
                        log.debug("Cluster {} updating seeds: chosen={}; potentialRunning={}", new Object[] {CassandraDatacenterImpl.this, result, potentialRunningSeeds});
                        return result;
                    }
                } else {
                    Set<Entity> result = trim(quorumSize, potentialSeeds);
                    if (log.isDebugEnabled()) log.debug("Cluster {} has reached seed quorum: seeds={}", new Object[] {CassandraDatacenterImpl.this, result});
                    return result;
                }
            }
        }
        private Set<Entity> trim(int num, Set<Entity> contenders) {
            // Prefer existing seeds wherever possible; otherwise accept any other contenders
            Set<Entity> currentSeeds = (getAttribute(CURRENT_SEEDS) != null) ? getAttribute(CURRENT_SEEDS) : ImmutableSet.<Entity>of();
            Set<Entity> result = Sets.newLinkedHashSet();
            result.addAll(Sets.intersection(currentSeeds, contenders));
            result.addAll(contenders);
            return ImmutableSet.copyOf(Iterables.limit(result, num));
        }
    };
    
    protected SeedTracker seedTracker = new SeedTracker();
    protected TokenGenerator tokenGenerator = null;

    public CassandraDatacenterImpl() {
    }

    @Override
    public void init() {
        super.init();

        /*
         * subscribe to hostname, and keep an accurate set of current seeds in a sensor;
         * then at nodes we set the initial seeds to be the current seeds when ready (non-empty)
         */
        subscriptions().subscribeToMembers(this, Attributes.HOSTNAME, new SensorEventListener<String>() {
            @Override
            public void onEvent(SensorEvent<String> event) {
                seedTracker.onHostnameChanged(event.getSource(), event.getValue());
            }
        });
        subscriptions().subscribe(this, DynamicGroup.MEMBER_REMOVED, new SensorEventListener<Entity>() {
            @Override public void onEvent(SensorEvent<Entity> event) {
                seedTracker.onMemberRemoved(event.getValue());
            }
        });
        subscriptions().subscribeToMembers(this, Attributes.SERVICE_UP, new SensorEventListener<Boolean>() {
            @Override
            public void onEvent(SensorEvent<Boolean> event) {
                seedTracker.onServiceUpChanged(event.getSource(), event.getValue());
            }
        });
        subscriptions().subscribeToMembers(this, Attributes.SERVICE_STATE_ACTUAL, new SensorEventListener<Lifecycle>() {
            @Override
            public void onEvent(SensorEvent<Lifecycle> event) {
                // trigger a recomputation also when lifecycle state changes, 
                // because it might not have ruled a seed as inviable when service up went true 
                // because service state was not yet running
                seedTracker.onServiceUpChanged(event.getSource(), Lifecycle.RUNNING==event.getValue());
            }
        });
        
        // Track the datacenters for this cluster
        subscriptions().subscribeToMembers(this, CassandraNode.DATACENTER_NAME, new SensorEventListener<String>() {
            @Override
            public void onEvent(SensorEvent<String> event) {
                Entity member = event.getSource();
                String dcName = event.getValue();
                if (dcName != null) {
                    Multimap<String, Entity> datacenterUsage = getAttribute(DATACENTER_USAGE);
                    Multimap<String, Entity> mutableDatacenterUsage = (datacenterUsage == null) ? LinkedHashMultimap.<String, Entity>create() : LinkedHashMultimap.create(datacenterUsage);
                    Optional<String> oldDcName = getKeyOfVal(mutableDatacenterUsage, member);
                    if (!(oldDcName.isPresent() && dcName.equals(oldDcName.get()))) {
                        mutableDatacenterUsage.values().remove(member);
                        mutableDatacenterUsage.put(dcName, member);
                        sensors().set(DATACENTER_USAGE, mutableDatacenterUsage);
                        sensors().set(DATACENTERS, Sets.newLinkedHashSet(mutableDatacenterUsage.keySet()));
                    }
                }
            }
            private <K,V> Optional<K> getKeyOfVal(Multimap<K,V> map, V val) {
                for (Map.Entry<K,V> entry : map.entries()) {
                    if (Objects.equal(val, entry.getValue())) {
                        return Optional.of(entry.getKey());
                    }
                }
                return Optional.absent();
            }
        });
        subscriptions().subscribe(this, DynamicGroup.MEMBER_REMOVED, new SensorEventListener<Entity>() {
            @Override public void onEvent(SensorEvent<Entity> event) {
                Entity entity = event.getSource();
                Multimap<String, Entity> datacenterUsage = getAttribute(DATACENTER_USAGE);
                if (datacenterUsage != null && datacenterUsage.containsValue(entity)) {
                    Multimap<String, Entity> mutableDatacenterUsage = LinkedHashMultimap.create(datacenterUsage);
                    mutableDatacenterUsage.values().remove(entity);
                    sensors().set(DATACENTER_USAGE, mutableDatacenterUsage);
                    sensors().set(DATACENTERS, Sets.newLinkedHashSet(mutableDatacenterUsage.keySet()));
                }
            }
        });
        
        getMutableEntityType().addEffector(EXECUTE_SCRIPT, new EffectorBody<String>() {
            @Override
            public String call(ConfigBag parameters) {
                return executeScript((String)parameters.getStringKey("commands"));
            }
        });
    }
    
    protected Supplier<Set<Entity>> getSeedSupplier() {
        Supplier<Set<Entity>> seedSupplier = getConfig(SEED_SUPPLIER);
        return (seedSupplier == null) ? defaultSeedSupplier : seedSupplier;
    }
    
    protected boolean useVnodes() {
        return Boolean.TRUE.equals(getConfig(USE_VNODES));
    }
    
    protected synchronized TokenGenerator getTokenGenerator() {
        if (tokenGenerator!=null) 
            return tokenGenerator;
        
        try {
            tokenGenerator = getConfig(TOKEN_GENERATOR_CLASS).newInstance();
            
            BigInteger shift = getConfig(TOKEN_SHIFT);
            if (shift==null) 
                shift = BigDecimal.valueOf(Math.random()).multiply(
                    new BigDecimal(tokenGenerator.range())).toBigInteger();
            tokenGenerator.setOrigin(shift);
            
            return tokenGenerator;
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }        
    }
    
    protected int getSeedQuorumSize() {
        Integer quorumSize = getConfig(INITIAL_QUORUM_SIZE);
        if (quorumSize!=null && quorumSize>0)
            return quorumSize;
        // default 2 is recommended, unless initial size is smaller
        return Math.min(Math.max(getConfig(INITIAL_SIZE), 1), DEFAULT_SEED_QUORUM);
    }

    @Override
    public Set<Entity> gatherPotentialSeeds() {
        return seedTracker.gatherPotentialSeeds();
    }

    @Override
    public Set<Entity> gatherPotentialRunningSeeds() {
        return seedTracker.gatherPotentialRunningSeeds();
    }

    /**
     * Sets the default {@link #MEMBER_SPEC} to describe the Cassandra nodes.
     */
    @Override
    protected EntitySpec<?> getMemberSpec() {
        return getConfig(MEMBER_SPEC, EntitySpec.create(CassandraNode.class));
    }

    @Override
    public String getClusterName() {
        return getAttribute(CLUSTER_NAME);
    }

    @Override
    public Collection<Entity> grow(int delta) {
        if (useVnodes()) {
            // nothing to do for token generator
        } else {
            if (getCurrentSize() == 0) {
                getTokenGenerator().growingCluster(delta);
            }
        }
        return super.grow(delta);
    }
    
    @Override
    protected Entity createNode(@Nullable Location loc, Map<?,?> flags) {
        Map<Object, Object> allflags = MutableMap.copyOf(flags);

        if (flags.containsKey("token") || flags.containsKey("cassandra.token")) {
            // TODO Delete in future version; was deprecated in 0.7.0; deleted config key in 0.9.0
            log.warn("Cassandra token no longer supported - use 'tokens' in "+CassandraDatacenterImpl.this);
        }
        if (flags.containsKey(CassandraNode.TOKENS) || flags.containsKey("tokens") || flags.containsKey("cassandra.tokens")) {
            // leave token config as-is
        } else if (!useVnodes()) {
            BigInteger token = getTokenGenerator().newToken();
            if (token != null) {
                allflags.put(CassandraNode.TOKENS, ImmutableSet.of(token));
            }
        }

        if ((flags.containsKey(CassandraNode.NUM_TOKENS_PER_NODE) || flags.containsKey("numTokensPerNode"))) {
            // leave num_tokens as-is
        } else if (useVnodes()) {
            Integer numTokensPerNode = getConfig(NUM_TOKENS_PER_NODE);
            allflags.put(CassandraNode.NUM_TOKENS_PER_NODE, numTokensPerNode);
        } else {
            allflags.put(CassandraNode.NUM_TOKENS_PER_NODE, 1);
        }
        
        return super.createNode(loc, allflags);
    }

    @Override
    protected Entity replaceMember(Entity member, Location memberLoc, Map<?, ?> extraFlags) {
        Set<BigInteger> oldTokens = ((CassandraNode) member).getTokens();
        Set<BigInteger> newTokens = (oldTokens != null && oldTokens.size() > 0) ? getTokenGenerator().getTokensForReplacementNode(oldTokens) : null;
        return super.replaceMember(member, memberLoc,  MutableMap.copyOf(extraFlags).add(CassandraNode.TOKENS, newTokens));
    }

    @Override
    public void start(Collection<? extends Location> locations) {
        Machines.warnIfLocalhost(locations, "CassandraCluster does not support multiple nodes on localhost, " +
                "due to assumptions Cassandra makes about the use of the same port numbers used across the cluster.");

        // force this to be set - even if it is using the default
        sensors().set(CLUSTER_NAME, getConfig(CLUSTER_NAME));
        
        super.start(locations);

        connectSensors();

        // TODO wait until all nodes which we think are up are consistent 
        // i.e. all known nodes use the same schema, as reported by
        // SshEffectorTasks.ssh("echo \"describe cluster;\" | /bin/cassandra-cli");
        // once we've done that we can revert to using 2 seed nodes.
        // see CassandraCluster.DEFAULT_SEED_QUORUM
        // (also ensure the cluster is ready if we are about to run a creation script)
        Time.sleep(getConfig(DELAY_BEFORE_ADVERTISING_CLUSTER));

        String scriptUrl = getConfig(CassandraNode.CREATION_SCRIPT_URL);
        if (Strings.isNonEmpty(scriptUrl)) {
            executeScript(new ResourceUtils(this).getResourceAsString(scriptUrl));
        }

        update();
    }

    protected void connectSensors() {
        connectEnrichers();
        
        policies().add(PolicySpec.create(MemberTrackingPolicy.class)
                .displayName("Cassandra Cluster Tracker")
                .configure("sensorsToTrack", ImmutableSet.of(Attributes.SERVICE_UP, Attributes.HOSTNAME, CassandraNode.THRIFT_PORT))
                .configure("group", this));
    }

    public static class MemberTrackingPolicy extends AbstractMembershipTrackingPolicy {
        @Override
        protected void onEntityChange(Entity member) {
            if (log.isDebugEnabled()) log.debug("Node {} updated in Cluster {}", member, this);
            ((CassandraDatacenterImpl)entity).update();
        }
        @Override
        protected void onEntityAdded(Entity member) {
            if (log.isDebugEnabled()) log.debug("Node {} added to Cluster {}", member, this);
            ((CassandraDatacenterImpl)entity).update();
        }
        @Override
        protected void onEntityRemoved(Entity member) {
            if (log.isDebugEnabled()) log.debug("Node {} removed from Cluster {}", member, this);
            ((CassandraDatacenterImpl)entity).update();
        }
    };

    @SuppressWarnings("unchecked")
    protected void connectEnrichers() {
        List<? extends List<? extends AttributeSensor<? extends Number>>> summingEnricherSetup = ImmutableList.of(
                ImmutableList.of(CassandraNode.READ_ACTIVE, READ_ACTIVE),
                ImmutableList.of(CassandraNode.READ_PENDING, READ_PENDING),
                ImmutableList.of(CassandraNode.WRITE_ACTIVE, WRITE_ACTIVE),
                ImmutableList.of(CassandraNode.WRITE_PENDING, WRITE_PENDING)
        );
        
        List<? extends List<? extends AttributeSensor<? extends Number>>> averagingEnricherSetup = ImmutableList.of(
                ImmutableList.of(CassandraNode.READS_PER_SECOND_LAST, READS_PER_SECOND_LAST_PER_NODE),
                ImmutableList.of(CassandraNode.WRITES_PER_SECOND_LAST, WRITES_PER_SECOND_LAST_PER_NODE),
                ImmutableList.of(CassandraNode.WRITES_PER_SECOND_IN_WINDOW, WRITES_PER_SECOND_IN_WINDOW_PER_NODE),
                ImmutableList.of(CassandraNode.READS_PER_SECOND_IN_WINDOW, READS_PER_SECOND_IN_WINDOW_PER_NODE),
                ImmutableList.of(CassandraNode.THRIFT_PORT_LATENCY, THRIFT_PORT_LATENCY_PER_NODE),
                ImmutableList.of(CassandraNode.THRIFT_PORT_LATENCY_IN_WINDOW, THRIFT_PORT_LATENCY_IN_WINDOW_PER_NODE),
                ImmutableList.of(CassandraNode.PROCESS_CPU_TIME_FRACTION_LAST, PROCESS_CPU_TIME_FRACTION_LAST_PER_NODE),
                ImmutableList.of(CassandraNode.PROCESS_CPU_TIME_FRACTION_IN_WINDOW, PROCESS_CPU_TIME_FRACTION_IN_WINDOW_PER_NODE)
        );
        
        for (List<? extends AttributeSensor<? extends Number>> es : summingEnricherSetup) {
            AttributeSensor<? extends Number> t = es.get(0);
            AttributeSensor<? extends Number> total = es.get(1);
            enrichers().add(Enrichers.builder()
                    .aggregating(t)
                    .publishing(total)
                    .fromMembers()
                    .computingSum()
                    .defaultValueForUnreportedSensors(null)
                    .valueToReportIfNoSensors(null)
                    .build());
        }
        
        for (List<? extends AttributeSensor<? extends Number>> es : averagingEnricherSetup) {
            AttributeSensor<Number> t = (AttributeSensor<Number>) es.get(0);
            AttributeSensor<Double> average = (AttributeSensor<Double>) es.get(1);
            enrichers().add(Enrichers.builder()
                    .aggregating(t)
                    .publishing(average)
                    .fromMembers()
                    .computingAverage()
                    .defaultValueForUnreportedSensors(null)
                    .valueToReportIfNoSensors(null)
                    .build());

        }
    }

    @Override
    public void stop() {
        disconnectSensors();
        
        super.stop();
    }
    
    protected void disconnectSensors() {
    }

    @Override
    public void update() {
        synchronized (mutex) {
            // Update our seeds, as necessary
            seedTracker.refreshSeeds();
            
            // Choose the first available cluster member to set host and port (and compute one-up)
            Optional<Entity> upNode = Iterables.tryFind(getMembers(), EntityPredicates.attributeEqualTo(SERVICE_UP, Boolean.TRUE));

            if (upNode.isPresent()) {
                sensors().set(HOSTNAME, upNode.get().getAttribute(Attributes.HOSTNAME));
                sensors().set(THRIFT_PORT, upNode.get().getAttribute(CassandraNode.THRIFT_PORT));

                List<String> currentNodes = getAttribute(CASSANDRA_CLUSTER_NODES);
                Set<String> oldNodes = (currentNodes != null) ? ImmutableSet.copyOf(currentNodes) : ImmutableSet.<String>of();
                Set<String> newNodes = MutableSet.<String>of();
                for (Entity member : getMembers()) {
                    if (member instanceof CassandraNode && Boolean.TRUE.equals(member.getAttribute(SERVICE_UP))) {
                        String hostname = member.getAttribute(Attributes.HOSTNAME);
                        Integer thriftPort = member.getAttribute(CassandraNode.THRIFT_PORT);
                        if (hostname != null && thriftPort != null) {
                            newNodes.add(HostAndPort.fromParts(hostname, thriftPort).toString());
                        }
                    }
                }
                if (Sets.symmetricDifference(oldNodes, newNodes).size() > 0) {
                    sensors().set(CASSANDRA_CLUSTER_NODES, MutableList.copyOf(newNodes));
                }
            } else {
                sensors().set(HOSTNAME, null);
                sensors().set(THRIFT_PORT, null);
                sensors().set(CASSANDRA_CLUSTER_NODES, Collections.<String>emptyList());
            }

            ServiceNotUpLogic.updateNotUpIndicatorRequiringNonEmptyList(this, CASSANDRA_CLUSTER_NODES);
        }
    }
    
    /**
     * For tracking our seeds. This gets fiddly! High-level logic is:
     * <ul>
     *   <li>If we have never reached quorum (i.e. have never published seeds), then continue to wait for quorum;
     *       because entity-startup may be blocking for this. This is handled by the seedSupplier.
     *   <li>If we previously reached quorum (i.e. have previousy published seeds), then always update;
     *       we never want stale/dead entities listed in our seeds.
     *   <li>If an existing seed looks unhealthy, then replace it.
     *   <li>If a new potential seed becomes available (and we're in need of more), then add it.
     * <ul>
     * 
     * Also note that {@link CassandraFabric} can take over, because it know about multiple sub-clusters!
     * It will provide a different {@link CassandraDatacenter#SEED_SUPPLIER}. Each time we think that our seeds
     * need to change, we call that. The fabric will call into {@link CassandraDatacenterImpl#gatherPotentialSeeds()}
     * to find out what's available.
     * 
     * @author aled
     */
    protected class SeedTracker {
        private final Map<Entity, Boolean> memberUpness = Maps.newLinkedHashMap();
        
        public void onMemberRemoved(Entity member) {
            Set<Entity> seeds = getSeeds();
            boolean maybeRemove = seeds.contains(member);
            memberUpness.remove(member);
            
            if (maybeRemove) {
                refreshSeeds();
            } else {
                if (log.isTraceEnabled()) log.trace("Seeds considered stable for cluster {} (node {} removed)", new Object[] {CassandraDatacenterImpl.this, member});
                return;
            }
        }
        public void onHostnameChanged(Entity member, String hostname) {
            Set<Entity> seeds = getSeeds();
            int quorum = getSeedQuorumSize();
            boolean isViable = isViableSeed(member);
            boolean maybeAdd = isViable && seeds.size() < quorum;
            boolean maybeRemove = seeds.contains(member) && !isViable;
            
            if (maybeAdd || maybeRemove) {
                refreshSeeds();
            } else {
                if (log.isTraceEnabled()) log.trace("Seeds considered stable for cluster {} (node {} changed hostname {})", new Object[] {CassandraDatacenterImpl.this, member, hostname});
                return;
            }
        }
        public void onServiceUpChanged(Entity member, Boolean serviceUp) {
            Boolean oldVal = memberUpness.put(member, serviceUp);
            if (Objects.equal(oldVal, serviceUp)) {
                if (log.isTraceEnabled()) log.trace("Ignoring duplicate service-up in "+CassandraDatacenterImpl.this+" for "+member+", "+serviceUp);
            }
            Set<Entity> seeds = getSeeds();
            int quorum = getSeedQuorumSize();
            boolean isViable = isViableSeed(member);
            boolean maybeAdd = isViable && seeds.size() < quorum;
            boolean maybeRemove = seeds.contains(member) && !isViable;
            
            if (log.isDebugEnabled())
                log.debug("Considering refresh of seeds for "+CassandraDatacenterImpl.this+" because "+member+" is now "+serviceUp+" ("+isViable+" / "+maybeAdd+" / "+maybeRemove+")");
            if (maybeAdd || maybeRemove) {
                refreshSeeds();
            } else {
                if (log.isTraceEnabled()) log.trace("Seeds considered stable for cluster {} (node {} changed serviceUp {})", new Object[] {CassandraDatacenterImpl.this, member, serviceUp});
                return;
            }
        }
        protected Set<Entity> getSeeds() {
            Set<Entity> result = getAttribute(CURRENT_SEEDS);
            return (result == null) ? ImmutableSet.<Entity>of() : result;
        }
        public void refreshSeeds() {
            Set<Entity> oldseeds = getAttribute(CURRENT_SEEDS);
            Set<Entity> newseeds = getSeedSupplier().get();
            if (Objects.equal(oldseeds, newseeds)) {
                if (log.isTraceEnabled()) log.debug("Seed refresh no-op for cluster {}: still={}", new Object[] {CassandraDatacenterImpl.this, oldseeds});
            } else {
                if (log.isDebugEnabled()) log.debug("Refreshing seeds of cluster {}: now={}; old={}", new Object[] {this, newseeds, oldseeds});
                sensors().set(CURRENT_SEEDS, newseeds);
                if (newseeds != null && newseeds.size() > 0) {
                    sensors().set(HAS_PUBLISHED_SEEDS, true);
                }
            }
        }
        public Set<Entity> gatherPotentialSeeds() {
            Set<Entity> result = Sets.newLinkedHashSet();
            for (Entity member : getMembers()) {
                if (isViableSeed(member)) {
                    result.add(member);
                }
            }
            if (log.isTraceEnabled()) log.trace("Viable seeds in Cluster {}: {}", new Object[] {result});
            return result;
        }
        public Set<Entity> gatherPotentialRunningSeeds() {
            Set<Entity> result = Sets.newLinkedHashSet();
            for (Entity member : getMembers()) {
                if (isRunningSeed(member)) {
                    result.add(member);
                }
            }
            if (log.isTraceEnabled()) log.trace("Viable running seeds in Cluster {}: {}", new Object[] {result});
            return result;
        }
        public boolean isViableSeed(Entity member) {
            // TODO would be good to reuse the better logic in ServiceFailureDetector
            // (e.g. if that didn't just emit a notification but set a sensor as well?)
            boolean managed = Entities.isManaged(member);
            String hostname = member.getAttribute(Attributes.HOSTNAME);
            boolean serviceUp = Boolean.TRUE.equals(member.getAttribute(Attributes.SERVICE_UP));
            Lifecycle serviceState = member.getAttribute(Attributes.SERVICE_STATE_ACTUAL);
            boolean hasFailed = !managed || (serviceState == Lifecycle.ON_FIRE) || (serviceState == Lifecycle.RUNNING && !serviceUp) || (serviceState == Lifecycle.STOPPED);
            boolean result = (hostname != null && !hasFailed);
            if (log.isTraceEnabled()) log.trace("Node {} in Cluster {}: viableSeed={}; hostname={}; serviceUp={}; serviceState={}; hasFailed={}", new Object[] {member, this, result, hostname, serviceUp, serviceState, hasFailed});
            return result;
        }
        public boolean isRunningSeed(Entity member) {
            boolean viableSeed = isViableSeed(member);
            boolean serviceUp = Boolean.TRUE.equals(member.getAttribute(Attributes.SERVICE_UP));
            Lifecycle serviceState = member.getAttribute(Attributes.SERVICE_STATE_ACTUAL);
            boolean result = viableSeed && serviceUp && serviceState == Lifecycle.RUNNING;
            if (log.isTraceEnabled()) log.trace("Node {} in Cluster {}: runningSeed={}; viableSeed={}; serviceUp={}; serviceState={}", new Object[] {member, this, result, viableSeed, serviceUp, serviceState});
            return result;
        }
    }
    
    @Override
    public String executeScript(String commands) {
        Entity someChild = Iterables.getFirst(getMembers(), null);
        if (someChild==null)
            throw new IllegalStateException("No Cassandra nodes available");
        // FIXME cross-etntity method-style calls such as below do not set up a queueing context (DynamicSequentialTask) 
//        return ((CassandraNode)someChild).executeScript(commands);
        return Entities.invokeEffector(this, someChild, CassandraNode.EXECUTE_SCRIPT, MutableMap.of("commands", commands)).getUnchecked();
    }
    
}
