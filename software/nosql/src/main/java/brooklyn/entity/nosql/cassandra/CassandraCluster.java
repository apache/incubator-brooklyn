/*
 * Copyright 2012 by Andrew Kennedy
 */
package brooklyn.entity.nosql.cassandra;

import java.util.Collection;
import java.util.List;
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
import brooklyn.event.basic.DependentConfiguration;
import brooklyn.location.Location;
import brooklyn.management.Task;
import brooklyn.util.MutableMap;

import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * A cluster of {@link CassandraNode}s based on {@link DynamicCluster} which can be resized by a policy if required.
 *
 * TODO add sensors with aggregated Cassandra statistics from cluster
 */
public class CassandraCluster extends DynamicCluster {
    /** serialVersionUID */
    private static final long serialVersionUID = 7288572450030871547L;

    private static final Logger log = LoggerFactory.getLogger(CassandraCluster.class);

    public static final MethodEffector<Void> UPDATE = new MethodEffector<Void>(CassandraCluster.class, "update");

    // Mutex for synchronizing during re-size operations
    private final Object mutex = new Object[0];
    
    private AbstractMembershipTrackingPolicy policy;

    public CassandraCluster(Map<?, ?> flags){
        this(flags, null);
    }

    public CassandraCluster(Entity owner){
        this(Maps.newHashMap(), owner);
    }

    public CassandraCluster(Map<?, ?> flags, Entity owner) {
        super(flags, owner);

        setFactory(new EntityFactory<CassandraNode>() {
            @Override
            public CassandraNode newEntity(Map factoryflags, Entity factoryOwner) {
                CassandraCluster cluster = (CassandraCluster) factoryOwner;
                Iterable<Entity> members = cluster.getMembers();
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
                    factoryflags.put(CassandraNode.SEEDS, seeds);
                } else if (tasks.size() > 0) {
                    factoryflags.put(CassandraNode.SEEDS, Iterables.get(tasks, 0));
                }
                return new CassandraNode(factoryflags, factoryOwner);
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

        // Stop all member nodes
        synchronized (mutex) {
            for (Entity member : getMembers()) {
                CassandraNode node = (CassandraNode) member;
                node.stop();
            }
        }

        setAttribute(Startable.SERVICE_UP, false);
    }
    
    // TODO is this required?
    @Description("Updates the cluster members ring tokens")
    public void update() {
//        synchronized (mutex) {
//            Iterable<Entity> members = getMembers();
//            int n = Iterables.size(members);
//            for (int i = 0; i < n; i++) {
//                CassandraNode node = (CassandraNode) Iterables.get(members, i);
//                Long token = (Long.MAX_VALUE / n) * i;
//                node.setToken(token.toString());
//            }
//        }
    }
}
