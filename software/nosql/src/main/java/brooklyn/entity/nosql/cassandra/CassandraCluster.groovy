/*
 * Copyright 2012 by Andrew Kennedy
 */
package brooklyn.entity.nosql.cassandra

import brooklyn.entity.Entity
import brooklyn.entity.basic.Description
import brooklyn.entity.basic.EntityFactory
import brooklyn.entity.basic.MethodEffector
import brooklyn.entity.group.AbstractMembershipTrackingPolicy
import brooklyn.entity.group.DynamicCluster
import brooklyn.entity.trait.Startable
import brooklyn.location.Location
import brooklyn.util.MutableMap

/**
 * A cluster of {@link CassandraServer}s based on {@link DynamicCluster} which can be resized by a policy if required.
 *
 * TODO add sensors with aggregated Cassandra statistics from cluster
 */
public class CassandraCluster extends DynamicCluster {

    public static final MethodEffector<Void> UPDATE = new MethodEffector(CassandraCluster.class, "update");

    AbstractMembershipTrackingPolicy trackingPolicy;

    public CassandraCluster(Map properties=[:], Entity owner=null) {
        super(properties, owner)

        factory = { Map props -> new CassandraServer(props) } as EntityFactory
    }

    @Override
    public void start(Collection<? extends Location> locations) {
        super.start(locations)

        trackingPolicy = new AbstractMembershipTrackingPolicy(MutableMap.of("name", "Cassandra Cluster Tracker")) {
            protected void onEntityChange(Entity member) { update(); }
            protected void onEntityAdded(Entity member) { update(); }
            protected void onEntityRemoved(Entity member) { update(); }
        };
        addPolicy(trackingPolicy);
        
        setAttribute(Startable.SERVICE_UP, true)
    }

    @Override
    public void stop() {
        super.stop()

        setAttribute(Startable.SERVICE_UP, false)
    }

    @Override
    public void restart() {
        throw new UnsupportedOperationException()
    }
    
    @Description("Updates the cluster members ring tokens")
    public synchronized void update() {
        int n = members.size();
        members.eachWithIndex { CassandraServer server, int i ->
            def token = BigInteger.TWO.pow(127) / n * i;
            server.setToken(token)
        }
    }
}
