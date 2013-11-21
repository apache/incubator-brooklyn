/*
 * Copyright 2012-2013 by Cloudsoft Corp.
 */
package brooklyn.entity.nosql.cassandra;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.enricher.CustomAggregatingEnricher;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.DynamicGroup;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityPredicates;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.entity.group.AbstractMembershipTrackingPolicy;
import brooklyn.entity.group.DynamicClusterImpl;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.event.AttributeSensor;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.location.Location;
import brooklyn.location.basic.Machines;
import brooklyn.util.ResourceUtils;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.text.Strings;
import brooklyn.util.time.Time;

import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;

/**
 * Implementation of {@link CassandraCluster}.
 * <p>
 * Several subtleties to note:
 * - a node may take some time after it is running and serving JMX to actually be contactable on its thrift port
 *   (so we wait for thrift port to be contactable)
 * - sometimes new nodes take a while to peer, and/or take a while to get a consistent schema
 *   (each up to 1m; often very close to the 1m) 
 */
public class CassandraClusterImpl extends DynamicClusterImpl implements CassandraCluster {

    /*
     * TODO Seed management is hard!
     *  - The ServiceRestarter is not doing customize(), so is not refreshing the seeds in cassandra.yaml.
     *    If we have two nodes that were seeds for each other and they both restart at the same time, we'll have a split brain.
     */
    
    private static final Logger log = LoggerFactory.getLogger(CassandraClusterImpl.class);

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
                int quorumSize = getQuorumSize();
                Set<Entity> potentialSeeds = gatherPotentialSeeds();
                Set<Entity> potentialRunningSeeds = gatherPotentialRunningSeeds();
                boolean stillWaitingForQuorum = (!hasPublishedSeeds) && (potentialSeeds.size() < quorumSize);
                
                if (stillWaitingForQuorum) {
                    if (log.isDebugEnabled()) log.debug("Not refresheed seeds of cluster {}, because still waiting for quorum (need {}; have {} potentials)", new Object[] {CassandraClusterImpl.class, getQuorumSize(), potentialSeeds.size()});
                    return ImmutableSet.of();
                } else if (hasPublishedSeeds) {
                    Set<Entity> currentSeeds = getAttribute(CURRENT_SEEDS);
                    if (getAttribute(SERVICE_STATE) == Lifecycle.STARTING) {
                        if (Sets.intersection(currentSeeds, potentialSeeds).isEmpty()) {
                            log.warn("Cluster {} lost all its seeds while starting! Subsequent failure likely, but changing seeds during startup would risk split-brain: seeds={}", new Object[] {this, currentSeeds});
                        }
                        return currentSeeds;
                    } else if (potentialRunningSeeds.isEmpty()) {
                        // TODO Could be race where nodes have only just returned from start() and are about to 
                        // transition to serviceUp; so don't just abandon all our seeds!
                        log.warn("Cluster {} has no running seeds (yet?); leaving seeds as-is; but risks split-brain if these seeds come back up!", new Object[] {this});
                        return currentSeeds;
                    } else {
                        Set<Entity> result = trim(quorumSize, potentialRunningSeeds);
                        log.debug("Cluster {} updating seeds: chosen={}; potentialRunning={}", new Object[] {this, result, potentialRunningSeeds});
                        return result;
                    }
                } else {
                    Set<Entity> result = trim(quorumSize, potentialSeeds);
                    if (log.isDebugEnabled()) log.debug("Cluster {} has reached seed quorum: seeds={}", new Object[] {this, result});
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
        private boolean containsDownEntity(Set<Entity> seeds) {
            for (Entity seed : seeds) {
                if (!seedTracker.isViableSeed(seed)) {
                    return true;
                }
            }
            return false;
        }
    };
    
    protected SeedTracker seedTracker = new SeedTracker();
    
    private AbstractMembershipTrackingPolicy policy;

    public CassandraClusterImpl() {
    }

    @Override
    public void init() {
        super.init();

        /*
         * subscribe to hostname, and keep an accurate set of current seeds in a sensor;
         * then at nodes we set the initial seeds to be the current seeds when ready (non-empty)
         */
        subscribeToMembers(this, Attributes.HOSTNAME, new SensorEventListener<String>() {
            @Override
            public void onEvent(SensorEvent<String> event) {
                seedTracker.onHostnameChanged(event.getSource(), event.getValue());
            }
        });
        subscribe(this, DynamicGroup.MEMBER_REMOVED, new SensorEventListener<Entity>() {
            @Override public void onEvent(SensorEvent<Entity> event) {
                seedTracker.onMemberRemoved(event.getValue());
            }
        });
        subscribeToMembers(this, Attributes.SERVICE_UP, new SensorEventListener<Boolean>() {
            @Override
            public void onEvent(SensorEvent<Boolean> event) {
                seedTracker.onServiceUpChanged(event.getSource(), event.getValue());
            }
        });
        
        // Track the datacenters for this cluster
        subscribeToMembers(this, CassandraNode.DATACENTER_NAME, new SensorEventListener<String>() {
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
                        setAttribute(DATACENTER_USAGE, mutableDatacenterUsage);
                        setAttribute(DATACENTERS, mutableDatacenterUsage.keySet());
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
        subscribe(this, DynamicGroup.MEMBER_REMOVED, new SensorEventListener<Entity>() {
            @Override public void onEvent(SensorEvent<Entity> event) {
                Entity entity = event.getSource();
                Multimap<String, Entity> datacenterUsage = getAttribute(DATACENTER_USAGE);
                if (datacenterUsage != null && datacenterUsage.containsValue(entity)) {
                    Multimap<String, Entity> mutableDatacenterUsage = LinkedHashMultimap.create(datacenterUsage);
                    mutableDatacenterUsage.values().remove(entity);
                    setAttribute(DATACENTER_USAGE, mutableDatacenterUsage);
                    setAttribute(DATACENTERS, mutableDatacenterUsage.keySet());
                }
            }
        });
    }
    
    protected Supplier<Set<Entity>> getSeedSupplier() {
        Supplier<Set<Entity>> seedSupplier = getConfig(SEED_SUPPLIER);
        return (seedSupplier == null) ? defaultSeedSupplier : seedSupplier;
    }
    
    protected int getQuorumSize() {
        Integer quorumSize = getConfig(INITIAL_QUORUM_SIZE);
        if (quorumSize!=null && quorumSize>0)
            return quorumSize;
        // default 2 is recommended, unless initial size is smaller
        return Math.min(getConfig(INITIAL_SIZE), DEFAULT_SEED_QUORUM);
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
    public void start(Collection<? extends Location> locations) {
        Machines.warnIfLocalhost(locations, "CassandraCluster does not support multiple nodes on localhost, " +
        		"due to assumptions Cassandra makes about the use of the same port numbers used across the cluster.");
        
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
        
        // track members
        policy = new AbstractMembershipTrackingPolicy(MutableMap.of("name", "Cassandra Cluster Tracker")) {
            @Override
            protected void onEntityChange(Entity member) {
                if (log.isDebugEnabled()) log.debug("Node {} updated in Cluster {}", member, this);
                update();
            }
            @Override
            protected void onEntityAdded(Entity member) {
                if (log.isDebugEnabled()) log.debug("Node {} added to Cluster {}", member, this);
                update();
            }
            @Override
            protected void onEntityRemoved(Entity member) {
                if (log.isDebugEnabled()) log.debug("Node {} removed from Cluster {}", member, this);
                update();
            }
        };
        addPolicy(policy);
        policy.setGroup(this);
    }
    
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
            CustomAggregatingEnricher<?,?> totaller = CustomAggregatingEnricher.newSummingEnricher(MutableMap.of("allMembers", true), t, total);
            addEnricher(totaller);
        }
        
        for (List<? extends AttributeSensor<? extends Number>> es : averagingEnricherSetup) {
            AttributeSensor<Number> t = (AttributeSensor<Number>) es.get(0);
            AttributeSensor<Double> average = (AttributeSensor<Double>) es.get(1);
            CustomAggregatingEnricher<?,?> averager = CustomAggregatingEnricher.newAveragingEnricher(MutableMap.of("allMembers", true), t, average, null);
            addEnricher(averager);
        }

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

    @Override
    public void update() {
        synchronized (mutex) {
            // Update our seeds, as necessary
            seedTracker.refreshSeeds();
            
            // Choose the first available cluster member to set host and port (and compute one-up)
            Optional<Entity> upNode = Iterables.tryFind(getMembers(), EntityPredicates.attributeEqualTo(SERVICE_UP, Boolean.TRUE));
            setAttribute(SERVICE_UP, upNode.isPresent());

            if (upNode.isPresent()) {
                setAttribute(HOSTNAME, upNode.get().getAttribute(Attributes.HOSTNAME));
                setAttribute(THRIFT_PORT, upNode.get().getAttribute(CassandraNode.THRIFT_PORT));
            } else {
                setAttribute(HOSTNAME, null);
                setAttribute(THRIFT_PORT, null);
            }

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
     * It will provide a different {@link CassandraCluster#SEED_SUPPLIER}. Each time we think that our seeds
     * need to change, we call that. The fabric will call into {@link CassandraClusterImpl#gatherPotentialSeeds()}
     * to find out what's available.
     * 
     * @author aled
     */
    protected class SeedTracker {
        public void onMemberRemoved(Entity member) {
            Set<Entity> seeds = getSeeds();
            boolean maybeRemove = seeds.contains(member);
            
            if (maybeRemove) {
                refreshSeeds();
            } else {
                if (log.isTraceEnabled()) log.trace("Seeds considered stable for cluster {} (node {} removed)", new Object[] {CassandraClusterImpl.this, member});
                return;
            }
        }
        public void onHostnameChanged(Entity member, String hostname) {
            Set<Entity> seeds = getSeeds();
            int quorum = getQuorumSize();
            boolean isViable = isViableSeed(member);
            boolean maybeAdd = isViable && seeds.size() < quorum;
            boolean maybeRemove = seeds.contains(member) && !isViable;
            
            if (maybeAdd || maybeRemove) {
                refreshSeeds();
            } else {
                if (log.isTraceEnabled()) log.trace("Seeds considered stable for cluster {} (node {} changed hostname {})", new Object[] {CassandraClusterImpl.this, member, hostname});
                return;
            }
            
            Supplier<Set<Entity>> seedSupplier = getSeedSupplier();
            setAttribute(CURRENT_SEEDS, seedSupplier.get());
        }
        public void onServiceUpChanged(Entity member, Boolean serviceUp) {
            Set<Entity> seeds = getSeeds();
            int quorum = getQuorumSize();
            boolean isViable = isViableSeed(member);
            boolean maybeAdd = isViable && seeds.size() < quorum;
            boolean maybeRemove = seeds.contains(member) && !isViable;
            
            if (log.isDebugEnabled())
                log.debug("Considering refresh of seeds for "+CassandraClusterImpl.this+" because "+member+" is now "+serviceUp+" ("+isViable+" / "+maybeAdd+" / "+maybeRemove+")");
            if (maybeAdd || maybeRemove) {
                refreshSeeds();
            } else {
                if (log.isTraceEnabled()) log.trace("Seeds considered stable for cluster {} (node {} changed serviceUp {})", new Object[] {CassandraClusterImpl.this, member, serviceUp});
                return;
            }
            
            Supplier<Set<Entity>> seedSupplier = getSeedSupplier();
            Set<Entity> newSeeds = seedSupplier.get();
            setAttribute(CURRENT_SEEDS, newSeeds);
            if (log.isDebugEnabled())
                log.debug("Seeds for "+CassandraClusterImpl.this+" now "+newSeeds);
        }
        protected Set<Entity> getSeeds() {
            Set<Entity> result = getAttribute(CURRENT_SEEDS);
            return (result == null) ? ImmutableSet.<Entity>of() : result;
        }
        public void refreshSeeds() {
            Set<Entity> oldseeds = getAttribute(CURRENT_SEEDS);
            Set<Entity> newseeds = getSeedSupplier().get();
            if (Objects.equal(oldseeds, newseeds)) {
                if (log.isTraceEnabled()) log.debug("Seed refresh no-op for cluster {}: still={}", new Object[] {CassandraClusterImpl.this, oldseeds});
            } else {
                if (log.isDebugEnabled()) log.debug("Refreshings seeds of cluster {}: now={}; old={}", new Object[] {this, newseeds, oldseeds});
                setAttribute(CURRENT_SEEDS, newseeds);
                if (newseeds != null && newseeds.size() > 0) {
                    setAttribute(HAS_PUBLISHED_SEEDS, true);
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
            Lifecycle serviceState = member.getAttribute(Attributes.SERVICE_STATE);
            boolean hasFailed = !managed || (serviceState == Lifecycle.ON_FIRE) || (serviceState == Lifecycle.RUNNING && !serviceUp) || (serviceState == Lifecycle.STOPPED);
            boolean result = (hostname != null && !hasFailed);
            if (log.isTraceEnabled()) log.trace("Node {} in Cluster {}: viableSeed={}; hostname={}; serviceUp={}; serviceState={}; hasFailed={}", new Object[] {member, this, result, hostname, serviceUp, serviceState, hasFailed});
            return result;
        }
        public boolean isRunningSeed(Entity member) {
            boolean viableSeed = isViableSeed(member);
            boolean serviceUp = Boolean.TRUE.equals(member.getAttribute(Attributes.SERVICE_UP));
            Lifecycle serviceState = member.getAttribute(Attributes.SERVICE_STATE);
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
