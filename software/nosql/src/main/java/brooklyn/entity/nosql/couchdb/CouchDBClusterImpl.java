/*
 * Copyright 2012-2013 by Cloudsoft Corp.
 */
package brooklyn.entity.nosql.couchdb;

import java.util.Collection;
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
import brooklyn.location.Location;
import brooklyn.util.MutableMap;

import com.google.common.collect.Iterables;

/**
 * Implementation of {@link CouchDBCluster}.
 */
public class CouchDBClusterImpl extends DynamicClusterImpl implements CouchDBCluster {
    /** serialVersionUID */
    private static final long serialVersionUID = 7288572450030871547L;

    private static final Logger log = LoggerFactory.getLogger(CouchDBClusterImpl.class);

    // Mutex for synchronizing during re-size operations
    private final Object mutex = new Object[0];

    private AbstractMembershipTrackingPolicy policy;

    public CouchDBClusterImpl() {
        this(MutableMap.of(), null);
    }
    public CouchDBClusterImpl(Map<?, ?> properties) {
        this(properties, null);
    }
    public CouchDBClusterImpl(Entity parent) {
        this(MutableMap.of(), parent);
    }
    public CouchDBClusterImpl(Map<?, ?> properties, Entity parent) {
        super(properties, parent);
    }

    /**
     * Sets the default {@link #MEMBER_SPEC} to describe the CouchDB nodes.
     */
    @Override
    protected EntitySpec<?> getMemberSpec() {
        return getConfig(MEMBER_SPEC, BasicEntitySpec.newInstance(CouchDBNode.class));
    }

    @Override
    public String getClusterName() {
        return getAttribute(CLUSTER_NAME);
    }

    @Override
    public void start(Collection<? extends Location> locations) {
        super.start(locations);

        policy = new AbstractMembershipTrackingPolicy(MutableMap.of("name", "CouchDB Cluster Tracker")) {
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
                CouchDBNode node = (CouchDBNode) member;
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
            CouchDBNode node = (CouchDBNode) Iterables.get(members, random.nextInt(n));
            setAttribute(HOSTNAME, node.getAttribute(Attributes.HOSTNAME));
            setAttribute(HTTP_PORT, node.getAttribute(CouchDBNode.HTTP_PORT));
        }
    }
}
