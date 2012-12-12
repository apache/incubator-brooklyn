/*
 * Copyright 2012 by Andrew Kennedy
 */
package brooklyn.entity.nosql.cassandra;

import java.math.BigInteger;
import java.util.Collection;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.Description;
import brooklyn.entity.basic.EntityFactory;
import brooklyn.entity.basic.MethodEffector;
import brooklyn.entity.group.AbstractMembershipTrackingPolicy;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.trait.Startable;
import brooklyn.location.Location;
import brooklyn.util.MutableMap;

import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;

/**
 * A cluster of {@link CassandraServer}s based on {@link DynamicCluster} which can be resized by a policy if required.
 *
 * TODO add sensors with aggregated Cassandra statistics from cluster
 */
public class CassandraCluster extends DynamicCluster {
    /** serialVersionUID */
    private static final long serialVersionUID = 7288572450030871547L;

    private static final Logger log = LoggerFactory.getLogger(CassandraCluster.class);

    public static final MethodEffector<Void> UPDATE = new MethodEffector<Void>(CassandraCluster.class, "update");

    private AbstractMembershipTrackingPolicy policy;

    public CassandraCluster(Map<?, ?> flags){
        this(flags, null);
    }

    public CassandraCluster(Entity owner){
        this(Maps.newHashMap(), owner);
    }

    public CassandraCluster(Map<?, ?> flags, Entity owner) {
        super(flags, owner);

        setFactory(new EntityFactory<CassandraServer>() {
            @Override
            public CassandraServer newEntity(Map factoryflags, Entity factoryOwner) {
                return new CassandraServer(factoryflags, factoryOwner);
            }
        });
    }

    @Override
    public void start(Collection<? extends Location> locations) {
        super.start(locations);

        policy = new AbstractMembershipTrackingPolicy(MutableMap.of("name", "Cassandra Cluster Tracker")) {
            @Override
            protected void onEntityChange(Entity member) { update(); }
            @Override
            protected void onEntityAdded(Entity member) { update(); }
            @Override
            protected void onEntityRemoved(Entity member) { update(); }
        };
        addPolicy(policy);
        policy.setGroup(this);
        
        setAttribute(Startable.SERVICE_UP, true);
    }

    @Override
    public void stop() {
        super.stop();

        setAttribute(Startable.SERVICE_UP, false);
    }

    @Override
    public void restart() {
        throw new UnsupportedOperationException();
    }
    
    @Description("Updates the cluster members ring tokens")
    public synchronized void update() {
        int n = getMembers().size();
        for (int i = 0; i < n; i++) {
            log.info("Update cassandra cluster member {} of {}", i, n);
            CassandraServer server = (CassandraServer) Iterables.get(getMembers(), i);
            BigInteger token = BigInteger.valueOf(2L).pow(127).divide(BigInteger.valueOf(n)).multiply(BigInteger.valueOf(i));
            server.setToken(token.toString());
        }
    }
}
