package brooklyn.entity.nosql.riak;

import static brooklyn.util.JavaGroovyEquivalents.groovyTruth;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.group.AbstractMembershipTrackingPolicy;
import brooklyn.entity.group.DynamicClusterImpl;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.trait.Startable;
import brooklyn.location.Location;
import brooklyn.policy.PolicySpec;

import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;


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

    protected EntitySpec<?> getMemberSpec() {
        EntitySpec<?> result = super.getMemberSpec();
        if (result!=null) return result;
        return EntitySpec.create(RiakNode.class);
    }
    
    protected void connectSensors() {
        addPolicy(PolicySpec.create(MemberTrackingPolicy.class)
                .displayName("Controller targets tracker")
                .configure("sensorsToTrack", ImmutableSet.of(RiakNode.SERVICE_UP))
                .configure("group", this));
    }

    public static class MemberTrackingPolicy extends AbstractMembershipTrackingPolicy {
        @Override protected void onEntityEvent(EventType type, Entity entity) {
            ((RiakClusterImpl)super.entity).onServerPoolMemberChanged(entity);
        }
    };

    protected synchronized void onServerPoolMemberChanged(Entity member) {
        if (log.isTraceEnabled()) log.trace("For {}, considering membership of {} which is in locations {}",
                new Object[]{this, member, member.getLocations()});

        if (belongsInServerPool(member)) {
            // TODO can we discover the nodes by asking the riak cluster, rather than assuming what we add will be in there?
            // TODO and can we do join as part of node starting?
            
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

                    log.info("Adding riak node {}: {}; {} to cluster", new Object[]{this, member, getRiakName(member)});

                } else {

                    //TODO: be wary of erreneous nodes but are still flagged 'in cluster'
                    //add the new node to be part of the riak cluster.
                    Optional<Entity> anyNodeInCluster = Iterables.tryFind(nodes.keySet(), new Predicate<Entity>() {
                        @Override
                        public boolean apply(@Nullable Entity node) {
                            return (node instanceof RiakNode && isMemberInCluster(node));
                        }
                    });

                    if (anyNodeInCluster.isPresent()) {
                        if (!nodes.containsKey(member) && !isMemberInCluster(member)) {

                            String anyNodeName = anyNodeInCluster.get().getAttribute(RiakNode.RIAK_NODE_NAME);
                            Entities.invokeEffectorWithArgs(this, member, RiakNode.JOIN_RIAK_CLUSTER, anyNodeName);

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
            if (nodes != null && nodes.containsKey(member)) {
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
            if (log.isTraceEnabled()) log.trace("Members of {}, checking {}, eliminating because not member", this, member);

            return false;
        }
        if (log.isTraceEnabled()) log.trace("Members of {}, checking {}, approving", this, member);

        return true;
    }

    private String getRiakName(Entity node) {
        return node.getAttribute(RiakNode.RIAK_NODE_NAME);
    }

    private Boolean isMemberInCluster(Entity member) {
        return Optional.fromNullable(member.getAttribute(RiakNode.RIAK_NODE_IN_CLUSTER)).or(Boolean.FALSE);
    }
}
