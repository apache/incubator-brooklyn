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
package brooklyn.entity.nosql.cassandra;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.DynamicGroup;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.basic.EntityPredicates;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.entity.group.AbstractMembershipTrackingPolicy;
import brooklyn.entity.group.DynamicFabricImpl;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.location.Location;
import brooklyn.policy.PolicySpec;
import brooklyn.util.collections.CollectionFunctionals;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.collections.MutableSet;
import brooklyn.util.time.Time;

import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;

/**
 * Implementation of {@link CassandraDatacenter}.
 * <p>
 * Serveral subtleties to note:
 * - a node may take some time after it is running and serving JMX to actually be contactable on its thrift port
 *   (so we wait for thrift port to be contactable)
 * - sometimes new nodes take a while to peer, and/or take a while to get a consistent schema
 *   (each up to 1m; often very close to the 1m) 
 */
public class CassandraFabricImpl extends DynamicFabricImpl implements CassandraFabric {

    private static final Logger log = LoggerFactory.getLogger(CassandraFabricImpl.class);

    // Mutex for synchronizing during re-size operations
    private final Object mutex = new Object[0];

    private MemberTrackingPolicy policy;

    private final Supplier<Set<Entity>> defaultSeedSupplier = new Supplier<Set<Entity>>() {
        @Override public Set<Entity> get() {
            // TODO Remove duplication from CassandraClusterImpl.defaultSeedSupplier
            Set<Entity> seeds = getAttribute(CURRENT_SEEDS);
            boolean hasPublishedSeeds = Boolean.TRUE.equals(getAttribute(HAS_PUBLISHED_SEEDS));
            int quorumSize = getSeedQuorumSize();
            
            // update seeds if we're not quorate; note this may not work for dynamically adding new datacenters
            // as we do not take a new seed from the new datacenter
            if (seeds == null || seeds.size() < quorumSize || containsDownEntity(seeds)) {
                Set<Entity> newseeds;
                Map<CassandraDatacenter,Set<Entity>> potentialSeeds = MutableMap.of();
                for (CassandraDatacenter member : Iterables.filter(getMembers(), CassandraDatacenter.class)) {
                    potentialSeeds.put(member, member.gatherPotentialSeeds());
                }
                
                if (hasPublishedSeeds) {
                    Set<Entity> currentSeeds = getAttribute(CURRENT_SEEDS);
                    Lifecycle serviceState = getAttribute(SERVICE_STATE);
                    if (serviceState == Lifecycle.STARTING) {
                        if (Sets.intersection(currentSeeds, ImmutableSet.copyOf(Iterables.concat(potentialSeeds.values()))).isEmpty()) {
                            log.warn("Fabric {} lost all its seeds while starting! Subsequent failure likely, but changing seeds during startup would risk split-brain: seeds={}", new Object[] {CassandraFabricImpl.this, currentSeeds});
                        }
                        newseeds = currentSeeds;
                    } else if (serviceState == Lifecycle.STOPPING || serviceState == Lifecycle.STOPPED) {
                        if (log.isTraceEnabled()) log.trace("Fabric {} ignoring any potential seed-changes, because {}: seeds={}", new Object[] {CassandraFabricImpl.this, serviceState, currentSeeds});
                        newseeds = currentSeeds;
                    } else if (potentialSeeds.isEmpty()) {
                        // TODO Could be race where nodes have only just returned from start() and are about to 
                        // transition to serviceUp; so don't just abandon all our seeds!
                        log.warn("Fabric {} has no seeds (after startup); leaving seeds as-is; but risks split-brain if these seeds come back up!", new Object[] {CassandraFabricImpl.this});
                        newseeds = currentSeeds;
                    } else if (!allNonEmpty(potentialSeeds.values())) {
                        log.warn("Fabric {} has datacenter with no seeds (after startup); leaving seeds as-is; but risks split-brain if these seeds come back up!", new Object[] {CassandraFabricImpl.this});
                        newseeds = currentSeeds;
                    } else {
                        Set<Entity> result = selectSeeds(quorumSize, potentialSeeds);
                        if (log.isDebugEnabled() && !Objects.equal(seeds, result)) {
                            log.debug("Fabric {} updating seeds: chosen={}; potential={}", new Object[] {CassandraFabricImpl.this, result, potentialSeeds});
                        }
                        newseeds = result;
                    }
                } else if (potentialSeeds.size() < quorumSize) {
                    if (log.isDebugEnabled()) log.debug("Not setting seeds of fabric {} yet, because still waiting for quorum (need {}; have {} potentials from {} members)", new Object[] {CassandraFabricImpl.this, quorumSize, potentialSeeds.size(), getMembers()});
                    newseeds = ImmutableSet.of();
                } else if (!allNonEmpty(potentialSeeds.values())) {
                    if (log.isDebugEnabled()) {
                        Map<CassandraDatacenter, Integer> datacenterCounts = Maps.transformValues(potentialSeeds, CollectionFunctionals.sizeFunction());
                        log.debug("Not setting seeds of fabric {} yet, because not all datacenters have seeds (sizes are {})", new Object[] {CassandraFabricImpl.this, datacenterCounts});
                    }
                    newseeds = ImmutableSet.of();
                } else {
                    // yay, we're quorate
                    Set<Entity> result = selectSeeds(quorumSize, potentialSeeds);
                    log.info("Fabric {} has reached seed quorum: seeds={}", new Object[] {CassandraFabricImpl.this, result});
                    newseeds = result;
                }
                
                if (!Objects.equal(seeds, newseeds)) {
                    setAttribute(CURRENT_SEEDS, newseeds);
                    
                    if (newseeds != null && newseeds.size() > 0) {
                        setAttribute(HAS_PUBLISHED_SEEDS, true);
                        
                        // Need to tell every datacenter that seeds are ready.
                        // Otherwise a datacenter might get no more changes (e.g. to nodes' hostnames etc), 
                        // and not call seedSupplier.get() again.
                        for (CassandraDatacenter member : Iterables.filter(getMembers(), CassandraDatacenter.class)) {
                            member.update();
                        }
                    }
                    return newseeds;
                } else {
                    return seeds;
                }
            } else {
                if (log.isTraceEnabled()) log.trace("Not refresheed seeds of fabric {}, because have quorum {} (of {} members), and none are down: seeds={}", 
                        new Object[] {CassandraFabricImpl.class, quorumSize, getMembers().size(), seeds});
                return seeds;
            }
        }
        private boolean allNonEmpty(Collection<? extends Collection<Entity>> contenders) {
            for (Collection<Entity> contender: contenders)
                if (contender.isEmpty()) return false;
            return true;
        }
        private Set<Entity> selectSeeds(int num, Map<CassandraDatacenter,? extends Collection<Entity>> contenders) {
            // Prefer existing seeds wherever possible;
            // otherwise prefer a seed from each sub-cluster;
            // otherwise accept any other contenders
            Set<Entity> currentSeeds = (getAttribute(CURRENT_SEEDS) != null) ? getAttribute(CURRENT_SEEDS) : ImmutableSet.<Entity>of();
            MutableSet<Entity> result = MutableSet.of();
            result.addAll(Sets.intersection(currentSeeds, ImmutableSet.copyOf(contenders.values())));
            for (CassandraDatacenter cluster : contenders.keySet()) {
                Set<Entity> contendersInCluster = Sets.newLinkedHashSet(contenders.get(cluster));
                if (contendersInCluster.size() > 0 && Sets.intersection(result, contendersInCluster).isEmpty()) {
                    result.add(Iterables.getFirst(contendersInCluster, null));
                }
            }
            result.addAll(Iterables.concat(contenders.values()));
            return ImmutableSet.copyOf(Iterables.limit(result, num));
        }
        private boolean containsDownEntity(Set<Entity> seeds) {
            for (Entity seed : seeds) {
                if (!isViableSeed(seed)) {
                    return true;
                }
            }
            return false;
        }
        public boolean isViableSeed(Entity member) {
            // TODO remove duplication from CassandraClusterImpl.SeedTracker.isViableSeed
            boolean managed = Entities.isManaged(member);
            String hostname = member.getAttribute(Attributes.HOSTNAME);
            boolean serviceUp = Boolean.TRUE.equals(member.getAttribute(Attributes.SERVICE_UP));
            Lifecycle serviceState = member.getAttribute(Attributes.SERVICE_STATE);
            boolean hasFailed = !managed || (serviceState == Lifecycle.ON_FIRE) || (serviceState == Lifecycle.RUNNING && !serviceUp) || (serviceState == Lifecycle.STOPPED);
            boolean result = (hostname != null && !hasFailed);
            if (log.isTraceEnabled()) log.trace("Node {} in Fabric {}: viableSeed={}; hostname={}; serviceUp={}; serviceState={}; hasFailed={}", new Object[] {member, CassandraFabricImpl.this, result, hostname, serviceUp, serviceState, hasFailed});
            return result;
        }
    };

    public CassandraFabricImpl() {
    }

    @Override
    public void init() {
        super.init();

        if (!getConfigRaw(CassandraDatacenter.SEED_SUPPLIER, true).isPresentAndNonNull())
            setConfig(CassandraDatacenter.SEED_SUPPLIER, getSeedSupplier());
        
        // track members
        policy = addPolicy(PolicySpec.create(MemberTrackingPolicy.class)
                .displayName("Cassandra Fabric Tracker")
                .configure("group", this));

        // Track first node's startup
        subscribeToMembers(this, CassandraDatacenter.FIRST_NODE_STARTED_TIME_UTC, new SensorEventListener<Long>() {
            @Override
            public void onEvent(SensorEvent<Long> event) {
                Long oldval = getAttribute(CassandraDatacenter.FIRST_NODE_STARTED_TIME_UTC);
                Long newval = event.getValue();
                if (oldval == null && newval != null) {
                    setAttribute(CassandraDatacenter.FIRST_NODE_STARTED_TIME_UTC, newval);
                    for (CassandraDatacenter member : Iterables.filter(getMembers(), CassandraDatacenter.class)) {
                        ((EntityInternal)member).setAttribute(CassandraDatacenter.FIRST_NODE_STARTED_TIME_UTC, newval);
                    }
                }
            }
        });
        
        // Track the datacenters for this cluster
        subscribeToMembers(this, CassandraDatacenter.DATACENTER_USAGE, new SensorEventListener<Multimap<String,Entity>>() {
            @Override
            public void onEvent(SensorEvent<Multimap<String,Entity>> event) {
                Multimap<String, Entity> usage = calculateDatacenterUsage();
                setAttribute(DATACENTER_USAGE, usage);
                setAttribute(DATACENTERS, usage.keySet());
            }
        });
        subscribe(this, DynamicGroup.MEMBER_REMOVED, new SensorEventListener<Entity>() {
            @Override public void onEvent(SensorEvent<Entity> event) {
                Multimap<String, Entity> usage = calculateDatacenterUsage();
                setAttribute(DATACENTER_USAGE, usage);
                setAttribute(DATACENTERS, usage.keySet());
            }
        });
    }

    public static class MemberTrackingPolicy extends AbstractMembershipTrackingPolicy {
        @Override
        protected void onEntityChange(Entity member) {
            if (log.isDebugEnabled()) log.debug("Location {} updated in Fabric {}", member, entity);
            ((CassandraFabricImpl)entity).update();
        }
        @Override
        protected void onEntityAdded(Entity member) {
            if (log.isDebugEnabled()) log.debug("Location {} added to Fabric {}", member, entity);
            ((CassandraFabricImpl)entity).update();
        }
        @Override
        protected void onEntityRemoved(Entity member) {
            if (log.isDebugEnabled()) log.debug("Location {} removed from Fabric {}", member, entity);
            ((CassandraFabricImpl)entity).update();
        }
    };

    protected int getSeedQuorumSize() {
        Integer quorumSize = getConfig(INITIAL_QUORUM_SIZE);
        if (quorumSize!=null && quorumSize>0)
            return quorumSize;

        int initialSizeSum = 0;
        for (CassandraDatacenter cluster : Iterables.filter(getMembers(), CassandraDatacenter.class)) {
            initialSizeSum += cluster.getConfig(CassandraDatacenter.INITIAL_SIZE);
        }
        if (initialSizeSum>5) initialSizeSum /= 2;
        else if (initialSizeSum>3) initialSizeSum -= 2;
        else if (initialSizeSum>2) initialSizeSum -= 1;
        
        return Math.min(Math.max(initialSizeSum, 1), CassandraFabric.DEFAULT_SEED_QUORUM);
    }

    /**
     * Sets the default {@link #MEMBER_SPEC} to describe the Cassandra sub-clusters.
     */
    @Override
    protected EntitySpec<?> getMemberSpec() {
        // Need to set the seedSupplier, even if the caller has overridden the CassandraCluster config
        // (unless they've explicitly overridden the seedSupplier as well!)
        // TODO probably don't need to anymore, as it is set on the Fabric here -- just make sure there is a default!
        EntitySpec<?> custom = getConfig(MEMBER_SPEC);
        if (custom == null) {
            return EntitySpec.create(CassandraDatacenter.class)
                    .configure(CassandraDatacenter.SEED_SUPPLIER, getSeedSupplier());
        } else if (custom.getConfig().containsKey(CassandraDatacenter.SEED_SUPPLIER) || custom.getFlags().containsKey("seedSupplier")) {
            return custom;
        } else {
            return EntitySpec.create(custom)
                    .configure(CassandraDatacenter.SEED_SUPPLIER, getSeedSupplier());
        }
    }
    
    @Override
    protected Entity createCluster(Location location, Map flags) {
        Function<Location, String> dataCenterNamer = getConfig(DATA_CENTER_NAMER);
        if (dataCenterNamer != null) {
            flags = ImmutableMap.builder()
                .putAll(flags)
                .put(CassandraNode.DATACENTER_NAME, dataCenterNamer.apply(location))
                .build();
        }
        return super.createCluster(location, flags);
    }

    /**
     * Prefers one node per location, and then others from anywhere.
     * Then trims result down to the "quorumSize".
     */
    public Supplier<Set<Entity>> getSeedSupplier() {
        return defaultSeedSupplier;
    }

    @Override
    public void start(Collection<? extends Location> locations) {
        super.start(locations);

        connectSensors();

        // TODO wait until all nodes which we think are up are consistent 
        // i.e. all known nodes use the same schema, as reported by
        // SshEffectorTasks.ssh("echo \"describe cluster;\" | /bin/cassandra-cli");
        // once we've done that we can revert to using 2 seed nodes.
        // see CassandraCluster.DEFAULT_SEED_QUORUM
        Time.sleep(getConfig(CassandraDatacenter.DELAY_BEFORE_ADVERTISING_CLUSTER));

        update();
    }

    protected void connectSensors() {
        connectEnrichers();
    }
    
    protected void connectEnrichers() {
        // TODO Aggregate across sub-clusters

        subscribeToMembers(this, SERVICE_UP, new SensorEventListener<Boolean>() {
            @Override public void onEvent(SensorEvent<Boolean> event) {
                setAttribute(SERVICE_UP, calculateServiceUp());
            }
        });
    }

    @Override
    public void stop() {
        disconnectSensors();
        
        super.stop();
    }
    
    protected void disconnectSensors() {
    }

    protected boolean calculateServiceUp() {
        Optional<Entity> upNode = Iterables.tryFind(getMembers(), EntityPredicates.attributeEqualTo(SERVICE_UP, Boolean.TRUE));
        return upNode.isPresent();
    }

    protected Multimap<String, Entity> calculateDatacenterUsage() {
        Multimap<String, Entity> result = LinkedHashMultimap.<String, Entity>create();
        for (CassandraDatacenter member : Iterables.filter(getMembers(), CassandraDatacenter.class)) {
            Multimap<String, Entity> memberUsage = member.getAttribute(CassandraDatacenter.DATACENTER_USAGE);
            if (memberUsage != null) result.putAll(memberUsage);
        }
        return result;
    }

    @Override
    public void update() {
        synchronized (mutex) {
            for (CassandraDatacenter member : Iterables.filter(getMembers(), CassandraDatacenter.class)) {
                member.update();
            }

            calculateServiceUp();

            // Choose the first available location to set host and port (and compute one-up)
            Optional<Entity> upNode = Iterables.tryFind(getMembers(), EntityPredicates.attributeEqualTo(SERVICE_UP, Boolean.TRUE));

            if (upNode.isPresent()) {
                setAttribute(HOSTNAME, upNode.get().getAttribute(Attributes.HOSTNAME));
                setAttribute(THRIFT_PORT, upNode.get().getAttribute(CassandraNode.THRIFT_PORT));
            }
        }
    }
}
