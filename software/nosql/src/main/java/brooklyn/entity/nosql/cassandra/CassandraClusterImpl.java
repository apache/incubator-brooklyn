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
import brooklyn.entity.proxying.EntitySpecs;
import brooklyn.entity.trait.Startable;
import brooklyn.event.basic.DependentConfiguration;
import brooklyn.location.Location;
import brooklyn.management.Task;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.task.DeferredSupplier;

import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

/**
 * Implementation of {@link CassandraCluster}.
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

        // This DeferredSupplier will return a comma separated list of all available
        // hostnames in the cluster or if none are available yet then a Task for the first
        // hostname in the list when it is ready. If there are no nodes yet it returns null.
        setConfig(SEEDS, new DeferredSupplier<Object>() {
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
        return getConfig(MEMBER_SPEC, EntitySpecs.spec(CassandraNode.class));
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
            protected void onEntityChange(Entity member) {
                if (log.isDebugEnabled()) log.debug("Node {} updated in Cluster {}", member, getClusterName());
                update();
            }
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

        setAttribute(Startable.SERVICE_UP, calculateServiceUp());
    }

    @Override
    protected boolean calculateServiceUp() {
        boolean up = false;
        for (Entity member : getMembers()) {
            if (Boolean.TRUE.equals(member.getAttribute(SERVICE_UP))) up = true;
        }
        return up;
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
