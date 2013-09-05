/*
 * Copyright 2012-2013 by Cloudsoft Corp.
 */
package brooklyn.entity.nosql.cassandra;

import java.util.Collection;
import java.util.List;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.group.AbstractMembershipTrackingPolicy;
import brooklyn.entity.group.DynamicClusterImpl;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.trait.Startable;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.location.Location;
import brooklyn.location.basic.Machines;
import brooklyn.util.collections.MutableMap;

import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

/**
 * Implementation of {@link CassandraCluster}.
 * <p>
 * Serveral subtleties to note:
 * - a node may take some time after it is running and serving JMX to actually be contactable on its thrift port
 * - sometimes (I think if nodes are started very near in time to each other in time, with >=2 seeds)
 *   the subsequent node does not successfully peer with the first; have not explored why
 * - when subsequent node is part of seed group, even if started late, it can take 1m to get a consistent schema 
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
                
                Iterable<Entity> members = getMembers();
                List<String> availableHostnames = Lists.newArrayList();
                for (Entity node : members) {
                    Optional<String> hostname = Machines.findSubnetOrPublicHostname(node);
                    if (hostname.isPresent()) {
                        availableHostnames.add(hostname.get());
                    }
                }
                
                if (!availableHostnames.isEmpty()) {
                    int quorumSize = getQuorumSize();
                    if (availableHostnames.size()>=quorumSize) {
                        while (availableHostnames.size()>quorumSize)
                            availableHostnames.remove(availableHostnames.size()-1);
                        setAttribute(CURRENT_SEEDS, Joiner.on(",").join(availableHostnames));
                        return;
                    }
                }
                
                // not quorate
                setAttribute(CURRENT_SEEDS, null);
            }

        });
    }
    
    protected int getQuorumSize() {
        Integer quorumSize = getConfig(INITIAL_QUORUM_SIZE);
        if (quorumSize!=null && quorumSize>0)
            return quorumSize;
        // default 2 is recommended, unless initial size is smaller
        // trying 1 to see if this helps subsequent node to get agreeing schemas sooner
        return Math.min(getConfig(INITIAL_SIZE), 1);
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

        setAttribute(Startable.SERVICE_UP, calculateServiceUp());
    }

    // handled instead by using quorum size as seeds (more efficient, and the code below didn't work anyways)
//    @Override
//    protected Collection<Entity> grow(int delta) {
//        if (getMembers().isEmpty() && delta>1) {
//            // can get intermittent SchemaDisagreementErrors otherwise
//            // more efficient would be to block on cassandra start (or even to ensure it is fixed in Cassandra);
//            // http://stackoverflow.com/questions/6770894/schemadisagreementexception
//            log.info("On initial creation of Cassandra cluster we are adding the first node before launching subsequent nodes");
//            List<Entity> result = new ArrayList<Entity>();
//            result.addAll(grow(1));
//            result.addAll(grow(delta-1));
//            return result;
//        } else {
//            return super.grow(delta);
//        }
//    }
    
    @Override
    protected boolean calculateServiceUp() {
        // TODO would be useful to have "n-up" semantics (policy?) available for groups ?
        for (Entity member : getMembers()) {
            if (Boolean.TRUE.equals(member.getAttribute(SERVICE_UP))) return true;
        }
        return false;
    }

    @Override
    public void update() {
        synchronized (mutex) {
            // Update the SERVICE_UP attribute
            boolean up = calculateServiceUp();
            setAttribute(SERVICE_UP, up);

            // Choose the first available cluster member to set host and port
            if (up) {
                CassandraNode node = (CassandraNode) Iterables.find(getMembers(), new Predicate<Entity>() {
                    @Override
                    public boolean apply(@Nullable Entity input) {
                        return input.getAttribute(SERVICE_UP);
                    }
                });
                setAttribute(HOSTNAME, node.getAttribute(Attributes.HOSTNAME));
                setAttribute(THRIFT_PORT, node.getAttribute(CassandraNode.THRIFT_PORT));
            } else {
                setAttribute(HOSTNAME, null);
                setAttribute(THRIFT_PORT, null);
            }
        }
    }
}
