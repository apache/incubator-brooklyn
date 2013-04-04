/*
 * Copyright 2012-2013 by Cloudsoft Corp.
 */
package brooklyn.entity.nosql.mongodb;

import brooklyn.entity.Entity;
import brooklyn.entity.group.AbstractMembershipTrackingPolicy;
import brooklyn.entity.group.DynamicClusterImpl;
import brooklyn.entity.proxying.BasicEntitySpec;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.trait.Startable;
import brooklyn.location.Location;
import brooklyn.util.MutableMap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Map;
import java.util.Random;

/**
 * Implementation of {@link MongoDbReplicaSet}.
 */
public class MongoDbReplicaSetImpl extends DynamicClusterImpl implements MongoDbReplicaSet {

    private static final Logger log = LoggerFactory.getLogger(MongoDbReplicaSetImpl.class);

    // Mutex for synchronizing during re-size operations
    private final Object mutex = new Object[0];

    private AbstractMembershipTrackingPolicy policy;

    private MongoDbServer master = null;

    public MongoDbReplicaSetImpl() {
        this(MutableMap.of(), null);
    }
    public MongoDbReplicaSetImpl(Map<?, ?> properties) {
        this(properties, null);
    }
    public MongoDbReplicaSetImpl(Entity parent) {
        this(MutableMap.of(), parent);
    }
    public MongoDbReplicaSetImpl(Map<?, ?> properties, Entity parent) {
        super(properties, parent);
    }

    @Override
    protected EntitySpec<?> getMemberSpec() {
        return getConfig(MEMBER_SPEC, BasicEntitySpec.newInstance(MongoDbServer.class));
    }

    @Override
    public String getReplicaSetName() {
        return getConfig(REPLICA_SET_NAME);
    }

    @Override
    public void start(Collection<? extends Location> locations) {
        super.start(locations);

        policy = new AbstractMembershipTrackingPolicy(MutableMap.of("name", "MongoDb Replica Set Tracker")) {
            @Override
            protected void onEntityChange(Entity member) { }
            @Override
            protected void onEntityAdded(Entity member) {
                synchronized (mutex) {
                    if (master == null) {
                        log.debug("Got the first node {} for replica set {}", member, getReplicaSetName());
                        master = (MongoDbServer) member;
                        master.initializeReplicaSet();
                    } else {
                        log.debug("Got an additional node {} for replica set {}", member, getReplicaSetName());
                        MongoDbServer server = (MongoDbServer) member;
                        master.addToReplicaSet(server);
                    }
                }
                if (log.isDebugEnabled()) log.debug("Node {} added to Replica Set {}", member, getReplicaSetName());
                update();
            }
            @Override
            protected void onEntityRemoved(Entity member) {
                if (log.isDebugEnabled()) log.debug("Node {} removed from Replica Set {}", member, getReplicaSetName());
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
                MongoDbServer node = (MongoDbServer) member;
                node.stop();
            }
        }

        setAttribute(Startable.SERVICE_UP, false);
    }

    @Override
    protected Map getCustomChildFlags() {
        return ImmutableMap.builder()
                .put(MongoDbServer.REPLICA_SET_NAME, getReplicaSetName())
                .putAll(super.getCustomChildFlags())
                .build();
    }

    private Random random = new Random();

    @Override
    public void update() {
        synchronized (mutex) {
            Iterable<Entity> members = getMembers();
            int n = Iterables.size(members);
            // Choose a random cluster member
            MongoDbServer node = (MongoDbServer) Iterables.get(members, random.nextInt(n));
        }
    }
}
