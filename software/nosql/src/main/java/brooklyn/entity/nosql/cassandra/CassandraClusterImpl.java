/*
 * Copyright 2012-2013 by Cloudsoft Corp.
 */
package brooklyn.entity.nosql.cassandra;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.group.AbstractMembershipTrackingPolicy;
import brooklyn.entity.group.DynamicClusterImpl;
import brooklyn.entity.proxying.BasicEntitySpec;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.trait.Startable;
import brooklyn.event.basic.DependentConfiguration;
import brooklyn.location.Location;
import brooklyn.management.Task;
import brooklyn.util.MutableMap;
import brooklyn.util.task.DeferredSupplier;

import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

/**
 * Implementation of {@link CassandraCluster}.
 */
public class CassandraClusterImpl extends DynamicClusterImpl implements CassandraCluster {
    /** serialVersionUID */
    private static final long serialVersionUID = 7288572450030871547L;

    private static final Logger log = LoggerFactory.getLogger(CassandraClusterImpl.class);

    // Mutex for synchronizing during re-size operations
    private final Object mutex = new Object[0];

    private AbstractMembershipTrackingPolicy policy;

    public CassandraClusterImpl() {
        this(MutableMap.of(), null);
    }
    public CassandraClusterImpl(Map<?, ?> properties) {
        this(properties, null);
    }
    public CassandraClusterImpl(Entity parent) {
        this(MutableMap.of(), parent);
    }
    public CassandraClusterImpl(Map<?, ?> properties, Entity parent) {
        super(properties, parent);

        // This DeferredSupplier will return a comma separated list of all available
        // hostnames in the cluster or if none are available yet then a Task for the first
        // hostname in the list when it is ready. If there are no nodes yet it returns null.
        setConfig(SEEDS, new DeferredSupplier() {
                public Object get() {
                    Iterable<Entity> members = getMembers();
                    List<String> nodes = Lists.newArrayList();
                    List<Task<String>> tasks = Lists.newArrayList();
                    for (Entity node : members) {
                        String hostname = node.getAttribute(CassandraNode.HOSTNAME);
                        if (hostname != null) {
                            nodes.add(hostname);
                        } else {
                            tasks.add(DependentConfiguration.attributeWhenReady(node, CassandraNode.HOSTNAME));
                        }
                    }
                    if (nodes.size() > 0) {
                        String seeds = Joiner.on(",").join(nodes);
                        return seeds;
                    } else if (tasks.size() > 0) {
                        return Iterables.get(tasks, 0);
                    } else {
                        return null;
                    }
                }
        });
    }

    /**
     * Sets the default {@link #MEMBER_SPEC} to describe the Cassandra nodes.
     */
    @Override
    protected EntitySpec<?> getMemberSpec() {
        return getConfig(MEMBER_SPEC, BasicEntitySpec.newInstance(CassandraNode.class));
    }

    @Override
    public String getClusterName() {
        return getAttribute(CLUSTER_NAME);
    }

    @Override
    public void start(Collection<? extends Location> locations) {
        super.start(locations);

        policy = new AbstractMembershipTrackingPolicy(MutableMap.of("name", "Cassandra Cluster Tracker")) {
            @Override
            protected void onEntityChange(Entity member) { }
            @Override
            protected void onEntityAdded(Entity member) {
                if (log.isDebugEnabled()) log.debug("Node {} added to Cluster {}", member, getClusterName());
                update();
            }
            @Override
            protected void onEntityRemoved(Entity member) {
                if (log.isDebugEnabled()) log.debug("Node {} removed from Cluster {}", member, getClusterName());
                update();
            }
        };
        addPolicy(policy);
        policy.setGroup(this);

        setAttribute(Startable.SERVICE_UP, true);
    }

    @Override
    public void stop() {
        super.stop();

        // Stop all member nodes
        synchronized (mutex) {
            for (Entity member : getMembers()) {
                CassandraNode node = (CassandraNode) member;
                node.stop();
            }
        }

        setAttribute(Startable.SERVICE_UP, false);
    }

    private Random random = new Random();

    @Override
    public void update() {
        synchronized (mutex) {
            Iterable<Entity> members = getMembers();
            int n = Iterables.size(members);
            // Choose a random cluster member
            CassandraNode node = (CassandraNode) Iterables.get(members, random.nextInt(n));
            setAttribute(HOSTNAME, node.getAttribute(Attributes.HOSTNAME));
            setAttribute(THRIFT_PORT, node.getAttribute(CassandraNode.THRIFT_PORT));
        }
    }
}
