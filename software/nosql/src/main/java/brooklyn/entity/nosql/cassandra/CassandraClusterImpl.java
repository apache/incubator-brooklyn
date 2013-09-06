/*
 * Copyright 2012-2013 by Cloudsoft Corp.
 */
package brooklyn.entity.nosql.cassandra;

import java.util.Collection;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.EntityPredicates;
import brooklyn.entity.group.AbstractMembershipTrackingPolicy;
import brooklyn.entity.group.DynamicClusterImpl;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.event.feed.ssh.SshFeed;
import brooklyn.location.Location;
import brooklyn.location.basic.Machines;
import brooklyn.util.collections.MutableMap;

import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

/**
 * Implementation of {@link CassandraCluster}.
 * <p>
 * Serveral subtleties to note:
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

    private SshFeed sshFeed;

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
    }
    
    protected int getQuorumSize() {
        Integer quorumSize = getConfig(INITIAL_QUORUM_SIZE);
        if (quorumSize!=null && quorumSize>0)
            return quorumSize;
        // default 2 is recommended, unless initial size is smaller
        return Math.min(getConfig(INITIAL_SIZE), DEFAULT_SEED_QUORUM);
    }

    protected String gatherSeeds() {
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
                return Joiner.on(",").join(Iterables.limit(availableHostnames, quorumSize));
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

        update();
    }

    protected void connectSensors() {
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
    
    @Override
    public void stop() {
        disconnectSensors();
        
        super.stop();
    }
    
    protected void disconnectSensors() {
        if (sshFeed!=null && sshFeed.isActive()) {
            sshFeed.stop();
            sshFeed = null;
        }
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
