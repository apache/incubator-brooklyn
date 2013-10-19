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
import brooklyn.entity.basic.EntityPredicates;
import brooklyn.entity.group.AbstractMembershipTrackingPolicy;
import brooklyn.entity.group.DynamicClusterImpl;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.event.AttributeSensor;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.location.Location;
import brooklyn.location.basic.Machines;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.collections.MutableSet;
import brooklyn.util.time.Time;

import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;

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

    private static final Logger log = LoggerFactory.getLogger(CassandraClusterImpl.class);

    // Mutex for synchronizing during re-size operations
    private final Object mutex = new Object[0];

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
                String newHostname = event.getValue();
                if (newHostname!=null) {
                    // node added
                    if (getAttribute(CURRENT_SEEDS)!=null)
                        // if we have enough seeds already then we don't need to do anything
                        return;
                }
                // node was removed, or added when we are not yet quorate; in either case let's find the seeds
                setAttribute(CURRENT_SEEDS, gatherSeeds());
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
    
    protected int getQuorumSize() {
        Integer quorumSize = getConfig(INITIAL_QUORUM_SIZE);
        if (quorumSize!=null && quorumSize>0)
            return quorumSize;
        // default 2 is recommended, unless initial size is smaller
        return Math.min(getConfig(INITIAL_SIZE), DEFAULT_SEED_QUORUM);
    }

    protected Set<Entity> gatherSeeds() {
        Iterable<Entity> members = getMembers();
        List<Entity> availableEntities = Lists.newArrayList();
        for (Entity node : members) {
            Optional<String> hostname = Machines.findSubnetOrPublicHostname(node);
            if (hostname.isPresent()) {
                availableEntities.add(node);
            }
        }
        
        if (!availableEntities.isEmpty()) {
            int quorumSize = getQuorumSize();
            if (availableEntities.size()>=quorumSize) {
                return MutableSet.copyOf(Iterables.limit(availableEntities, quorumSize));
            }
        }
        
        // not quorate
        return null;
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
        Time.sleep(DELAY_BEFORE_ADVERTISING_CLUSTER);

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
}
