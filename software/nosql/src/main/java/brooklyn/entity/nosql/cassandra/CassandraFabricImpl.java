/*
 * Copyright 2012-2013 by Cloudsoft Corp.
 */
package brooklyn.entity.nosql.cassandra;

import java.util.Collection;
import java.util.List;
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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Lists;
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
     * Sets the default {@link #MEMBER_SPEC} to describe the Cassandra nodes.
     */
    @Override
    protected EntitySpec<?> getMemberSpec() {
        return getConfig(MEMBER_SPEC, EntitySpec.create(CassandraCluster.class)
                .configure(CassandraCluster.SEED_SUPPLIER, getSeedSupplier()));
    }

    /**
     * Prefers one node per location, and then others from anywhere.
     * Then trims result down to the "quorumSize".
     */
    protected Supplier<Set<Entity>> getSeedSupplier() {
        return new Supplier<Set<Entity>>() {
            @Override public Set<Entity> get() {
                Set<Entity> seeds = getAttribute(CURRENT_SEEDS);
                if (seeds == null || seeds.isEmpty() || containsDownEntity(seeds)) {
                    seeds = gatherSeeds();
                    setAttribute(CURRENT_SEEDS, seeds);
                }
                return seeds;
            }
            private boolean containsDownEntity(Set<Entity> seeds) {
                for (Entity entity : seeds) {
                    boolean managed = Entities.isManaged(entity);
                    boolean up = Boolean.TRUE.equals(entity.getAttribute(Attributes.SERVICE_UP));
                    Lifecycle state = entity.getAttribute(Attributes.SERVICE_STATE);
                    if (!managed || (!up && (state == Lifecycle.ON_FIRE || state == Lifecycle.RUNNING))) {
                        return true;
                    }
                }
                return false;
            }
        };
    }

    protected Set<Entity> gatherSeeds() {
        List<Entity> result = Lists.newArrayList();
        Set<Entity> otherPotentials = Sets.newLinkedHashSet();
        for (CassandraCluster member : Iterables.filter(getMembers(), CassandraCluster.class)) {
            Collection<Entity> potentialSeeds = member.gatherPotentialSeeds();
            if (potentialSeeds.size() > 0) {
                result.add(Iterables.get(potentialSeeds, 0));
                otherPotentials.addAll(ImmutableList.copyOf(potentialSeeds).subList(1, potentialSeeds.size()));
            }
        }
        result.addAll(otherPotentials);
        
        int quorumSize = getQuorumSize(getMembers());
        if (result.size() >= quorumSize) {
            return ImmutableSet.copyOf(result.subList(0, quorumSize));
        } else {
            return ImmutableSet.of();
        }
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
        Time.sleep(CassandraCluster.DELAY_BEFORE_ADVERTISING_CLUSTER);

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
