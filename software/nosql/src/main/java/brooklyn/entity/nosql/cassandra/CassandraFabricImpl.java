/*
 * Copyright 2012-2013 by Cloudsoft Corp.
 */
package brooklyn.entity.nosql.cassandra;

import java.util.Collection;
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
import brooklyn.util.collections.MutableMap;
import brooklyn.util.time.Time;

import com.google.common.base.Optional;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;

/**
 * Implementation of {@link CassandraCluster}.
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

    private AbstractMembershipTrackingPolicy policy;

    private final Supplier<Set<Entity>> defaultSeedSupplier = new Supplier<Set<Entity>>() {
        @Override public Set<Entity> get() {
            // TODO Remove duplication from CassandraClusterImpl.defaultSeedSupplier
            Set<Entity> seeds = getAttribute(CURRENT_SEEDS);
            boolean hasPublishedSeeds = Boolean.TRUE.equals(getAttribute(HAS_PUBLISHED_SEEDS));
            int quorumSize = getQuorumSize(getMembers());
            
            if (seeds == null || seeds.size() < quorumSize || containsDownEntity(seeds)) {
                Set<Entity> newseeds;
                Multimap<CassandraCluster,Entity> potentialSeeds = LinkedHashMultimap.create();
                Multimap<CassandraCluster,Entity> potentialRunningSeeds = LinkedHashMultimap.create();
                for (CassandraCluster member : Iterables.filter(getMembers(), CassandraCluster.class)) {
                    potentialSeeds.putAll(member, member.gatherPotentialSeeds());
                    potentialRunningSeeds.putAll(member, member.gatherPotentialSeeds());
                }
                boolean stillWaitingForQuorum = (!hasPublishedSeeds) && (potentialSeeds.size() < quorumSize);
                
                if (stillWaitingForQuorum) {
                    if (log.isDebugEnabled()) log.debug("Not refresheed seeds of fabric {}, because still waiting for quorum (need {}; have {} potentials)", new Object[] {CassandraFabricImpl.class, quorumSize, potentialSeeds.size()});
                    newseeds = ImmutableSet.of();
                } else if (hasPublishedSeeds) {
                    Set<Entity> currentSeeds = getAttribute(CURRENT_SEEDS);
                    if (getAttribute(SERVICE_STATE) == Lifecycle.STARTING) {
                        if (Sets.intersection(currentSeeds, ImmutableSet.copyOf(potentialSeeds.values())).isEmpty()) {
                            log.warn("Cluster {} lost all its seeds while starting! Subsequent failure likely, but changing seeds during startup would risk split-brain: seeds={}", new Object[] {this, currentSeeds});
                        }
                        newseeds = currentSeeds;
                    } else if (potentialRunningSeeds.isEmpty()) {
                        // TODO Could be race where nodes have only just returned from start() and are about to 
                        // transition to serviceUp; so don't just abandon all our seeds!
                        log.warn("Cluster {} has no running seeds (yet?); leaving seeds as-is; but risks split-brain if these seeds come back up!", new Object[] {this});
                        newseeds = currentSeeds;
                    } else {
                        Set<Entity> result = trim(quorumSize, potentialRunningSeeds);
                        log.debug("Cluster {} updating seeds: chosen={}; potentialRunning={}", new Object[] {this, result, potentialRunningSeeds});
                        newseeds = result;
                    }
                } else {
                    Set<Entity> result = trim(quorumSize, potentialSeeds);
                    if (log.isDebugEnabled()) log.debug("Cluster {} has reached seed quorum: seeds={}", new Object[] {this, result});
                    newseeds = result;
                }
                
                setAttribute(CURRENT_SEEDS, newseeds);
                return newseeds;
            } else {
                if (log.isDebugEnabled()) log.debug("Not refresheed seeds of fabric {}, because have quorum {}, and none are down: seeds=", new Object[] {CassandraFabricImpl.class, quorumSize, seeds});
                return seeds;
            }
        }
        private Set<Entity> trim(int num, Multimap<CassandraCluster,Entity> contenders) {
            // Prefer existing seeds wherever possible;
            // otherwise prefer a seed from each sub-cluster;
            // otherwise accept any other contenders
            Set<Entity> currentSeeds = (getAttribute(CURRENT_SEEDS) != null) ? getAttribute(CURRENT_SEEDS) : ImmutableSet.<Entity>of();
            Set<Entity> result = Sets.newLinkedHashSet();
            result.addAll(Sets.intersection(currentSeeds, ImmutableSet.copyOf(contenders.values())));
            for (CassandraCluster cluster : contenders.keySet()) {
                Set<Entity> contendersInCluster = Sets.newLinkedHashSet(contenders.get(cluster));
                if (contendersInCluster.size() > 0 && Sets.intersection(result, contendersInCluster).isEmpty()) {
                    result.add(Iterables.getFirst(contendersInCluster, null));
                }
            }
            result.addAll(contenders.values());
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
            if (log.isTraceEnabled()) log.trace("Node {} in Cluster {}: viableSeed={}; hostname={}; serviceUp={}; serviceState={}; hasFailed={}", new Object[] {member, this, result, hostname, serviceUp, serviceState, hasFailed});
            return result;
        }
    };

    public CassandraFabricImpl() {
    }

    @Override
    public void init() {
        super.init();

        // track members
        policy = new AbstractMembershipTrackingPolicy(MutableMap.of("name", "Cassandra Cluster Tracker")) {
            @Override
            protected void onEntityChange(Entity member) {
                if (log.isDebugEnabled()) log.debug("Location {} updated in Cluster {}", member, this);
                update();
            }
            @Override
            protected void onEntityAdded(Entity member) {
                if (log.isDebugEnabled()) log.debug("Location {} added to Cluster {}", member, this);
                update();
            }
            @Override
            protected void onEntityRemoved(Entity member) {
                if (log.isDebugEnabled()) log.debug("Location {} removed from Cluster {}", member, this);
                update();
            }
        };
        addPolicy(policy);
        policy.setGroup(this);

        // Track first node's startup
        subscribeToMembers(this, CassandraCluster.FIRST_NODE_STARTED_TIME_UTC, new SensorEventListener<Long>() {
            @Override
            public void onEvent(SensorEvent<Long> event) {
                Long oldval = getAttribute(CassandraCluster.FIRST_NODE_STARTED_TIME_UTC);
                Long newval = event.getValue();
                if (oldval == null && newval != null) {
                    setAttribute(CassandraCluster.FIRST_NODE_STARTED_TIME_UTC, newval);
                    for (CassandraCluster member : Iterables.filter(getMembers(), CassandraCluster.class)) {
                        ((EntityInternal)member).setAttribute(CassandraCluster.FIRST_NODE_STARTED_TIME_UTC, newval);
                    }
                }
            }
        });
        
        // Track the datacenters for this cluster
        subscribeToMembers(this, CassandraCluster.DATACENTER_USAGE, new SensorEventListener<Multimap<String,Entity>>() {
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
    
    protected int getQuorumSize(Collection<Entity> members) {
        Integer quorumSize = getConfig(INITIAL_QUORUM_SIZE);
        if (quorumSize!=null && quorumSize>0)
            return quorumSize;
        
        // default 2 is recommended, unless initial size is smaller
        int initialSizeSum = 0;
        for (CassandraCluster member : Iterables.filter(getMembers(), CassandraCluster.class)) {
            initialSizeSum += member.getConfig(CassandraCluster.INITIAL_SIZE);
        }
        return Math.min(initialSizeSum, CassandraCluster.DEFAULT_SEED_QUORUM);
    }

    /**
     * Sets the default {@link #MEMBER_SPEC} to describe the Cassandra sub-clusters.
     */
    @Override
    protected EntitySpec<?> getMemberSpec() {
        // Need to set the seedSupplier, even if the caller has overridden the CassandraCluster config
        // (unless they've explicitly overridden the seedSupplier as well!)
        EntitySpec<?> custom = getConfig(MEMBER_SPEC);
        if (custom == null) {
            return EntitySpec.create(CassandraCluster.class)
                    .configure(CassandraCluster.SEED_SUPPLIER, getSeedSupplier());
        } else if (custom.getConfig().containsKey(CassandraCluster.SEED_SUPPLIER) || custom.getFlags().containsKey("seedSupplier")) {
            return custom;
        } else {
            return EntitySpec.create(CassandraCluster.class)
                    .configure(CassandraCluster.SEED_SUPPLIER, getSeedSupplier());
        }
    }

    /**
     * Prefers one node per location, and then others from anywhere.
     * Then trims result down to the "quorumSize".
     */
    protected Supplier<Set<Entity>> getSeedSupplier() {
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
        Time.sleep(getConfig(CassandraCluster.DELAY_BEFORE_ADVERTISING_CLUSTER));

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
        for (CassandraCluster member : Iterables.filter(getMembers(), CassandraCluster.class)) {
            Multimap<String, Entity> memberUsage = member.getAttribute(CassandraCluster.DATACENTER_USAGE);
            if (memberUsage != null) result.putAll(memberUsage);
        }
        return result;
    }

    @Override
    public void update() {
        synchronized (mutex) {
            for (CassandraCluster member : Iterables.filter(getMembers(), CassandraCluster.class)) {
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
