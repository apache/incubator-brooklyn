package brooklyn.entity.nosql.riak;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.group.AbstractMembershipTrackingPolicy;
import brooklyn.entity.group.DynamicClusterImpl;
import brooklyn.entity.trait.Startable;
import brooklyn.location.Location;
import brooklyn.util.collections.MutableMap;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static brooklyn.util.JavaGroovyEquivalents.groovyTruth;


public class RiakClusterImpl extends DynamicClusterImpl implements RiakCluster {
    private static final Logger log = LoggerFactory.getLogger(RiakClusterImpl.class);
    private AtomicBoolean isFirstNodeSet = new AtomicBoolean();

    public void init() {
        log.info("Initializing the riak cluster...");
        super.init();
    }

    @Override
    public void start(Collection<? extends Location> locations) {

        super.start(locations);
        connectSensors();
    }

    protected void connectSensors() {

        Map<String, Object> flags = MutableMap.<String, Object>builder()
                .put("name", "Controller targets tracker")
                .put("sensorsToTrack", ImmutableSet.of(RiakNode.SERVICE_UP))
                .build();

        AbstractMembershipTrackingPolicy serverPoolMemberTrackerPolicy = new AbstractMembershipTrackingPolicy(flags) {
            protected void onEntityChange(Entity member) {
                onServerPoolMemberChanged(member);
            }

            protected void onEntityAdded(Entity member) {
                onServerPoolMemberChanged(member);
            }

            protected void onEntityRemoved(Entity member) {
                onServerPoolMemberChanged(member);
            }
        };

        addPolicy(serverPoolMemberTrackerPolicy);
        serverPoolMemberTrackerPolicy.setGroup(this);
    }

    protected synchronized void onServerPoolMemberChanged(Entity member) {
        if (log.isTraceEnabled()) log.trace("For {}, considering membership of {} which is in locations {}",
                new Object[]{this, member, member.getLocations()});

        if (belongsInServerPool(member)) {
            Map<Entity, String> nodes = getAttribute(RIAK_CLUSTER_NODES);
            if (nodes == null) nodes = Maps.newLinkedHashMap();
            String riakName = getRiakName(member);

            if (riakName == null) {
                log.error("Unable to get riak name for node: {}", member.getId());
            } else {

                //flag a first node to be the first node in the riak cluster.
                if (!isFirstNodeSet.get()) {
                    nodes.put(member, riakName);
                    setAttribute(RIAK_CLUSTER_NODES, nodes);

                    ((EntityInternal) member).setAttribute(RiakNode.RIAK_NODE_IN_CLUSTER, Boolean.TRUE);
                    isFirstNodeSet.set(true);

                } else {
                    //TODO: be wary of erreneous nodes but are still flagged 'in cluster'
                    //add the new node to be part of the riak cluster.
                    Optional<Entity> anyNodeInCluster = Iterables.tryFind(nodes.keySet(), new Predicate<Entity>() {
                        @Override
                        public boolean apply(@Nullable Entity node) {
                            return (node instanceof RiakNode && node.getAttribute(RiakNode.RIAK_NODE_IN_CLUSTER)) ? true : false;
                        }
                    });

                    //invoke join cluster operation on newly added member.
                    if (anyNodeInCluster.isPresent()) {
                        if (!isMemberInCluster(member)) {
                            Entities.invokeEffectorWithArgs(this, member, RiakNode.JOIN_RIAK_CLUSTER, (RiakNode) anyNodeInCluster.get());
                            nodes.put(member, riakName);
                            setAttribute(RIAK_CLUSTER_NODES, nodes);
                            log.info("Adding riak node {}: {}; {} to cluster", new Object[]{this, member, getRiakName(member)});
                        }
                    } else {
                        log.error("entity {}: is not present", member.getId());
                    }
                }
            }
        } else {
            Map<Entity, String> nodes = getAttribute(RIAK_CLUSTER_NODES);
            if (nodes != null) {
                if (isMemberInCluster(member))
                    Entities.invokeEffector(this, member, RiakNode.LEAVE_RIAK_CLUSTER);

                nodes.remove(member);

                setAttribute(RIAK_CLUSTER_NODES, nodes);
                log.info("Removing riak node {}: {}; {} from cluster", new Object[]{this, member, getRiakName(member)});

            }
        }
        if (log.isTraceEnabled()) log.trace("Done {} checkEntity {}", this, member);
    }

    protected boolean belongsInServerPool(Entity member) {
        if (!groovyTruth(member.getAttribute(Startable.SERVICE_UP))) {
            if (log.isTraceEnabled()) log.trace("Members of {}, checking {}, eliminating because not up", this, member);
            return false;
        }
        if (!getMembers().contains(member)) {
            if (log.isTraceEnabled())
                log.trace("Members of {}, checking {}, eliminating because not member", this, member);
            return false;
        }
        if (log.isTraceEnabled()) log.trace("Members of {}, checking {}, approving", this, member);
        return true;
    }

    private String getRiakName(Entity node) {
        return ((RiakNode) node).getAttribute(RiakNode.RIAK_NODE_NAME);
    }

    private Boolean isMemberInCluster(Entity member)
    {
        return (Optional.fromNullable(member.getAttribute(RiakNode.RIAK_NODE_IN_CLUSTER)).or(Boolean.FALSE));
    }
}
