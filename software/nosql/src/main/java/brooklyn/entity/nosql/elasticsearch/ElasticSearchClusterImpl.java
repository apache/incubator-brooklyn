package brooklyn.entity.nosql.elasticsearch;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.group.AbstractMembershipTrackingPolicy;
import brooklyn.entity.group.DynamicClusterImpl;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.location.Location;
import brooklyn.policy.PolicySpec;
import brooklyn.util.text.Strings;

public class ElasticSearchClusterImpl extends DynamicClusterImpl implements ElasticSearchCluster {
    
    private AtomicInteger nextMemberId = new AtomicInteger(0);
    private MemberTrackingPolicy policy;
    
    public ElasticSearchClusterImpl() {
        
    }
    
    @Override
    public void init() {
        policy = addPolicy(PolicySpec.create(MemberTrackingPolicy.class)
                .displayName(getName() + " membership tracker")
                .configure("group", this) 
                .configure(AbstractMembershipTrackingPolicy.NOTIFY_ON_DUPLICATES, false));
    }
    
    @Override
    public void start(Collection<? extends Location> locations) {
        super.start(locations);
    }
    
    @Override
    public void stop() {
        if (policy != null) {
            removePolicy(policy);
        }
        super.stop();
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
    protected EntitySpec<?> getMemberSpec() {
        @SuppressWarnings("unchecked")
        EntitySpec<ElasticSearchNode> spec = (EntitySpec<ElasticSearchNode>)getConfig(MEMBER_SPEC, EntitySpec.create(ElasticSearchNode.class));
        
        spec.configure(ElasticSearchNode.CLUSTER_NAME, getConfig(ElasticSearchClusterImpl.CLUSTER_NAME))
            .configure(ElasticSearchNode.MULTICAST_ENABLED, false)
            .configure(ElasticSearchNode.UNICAST_ENABLED, false)
            .configure(ElasticSearchNode.NODE_NAME, "elasticsearch-" + nextMemberId.incrementAndGet());
        
        return spec;
    }
    
    @Override
    public String getName() {
        return getConfig(CLUSTER_NAME);
    }
    
    private void resetCluster() {
        String nodeList = "";
        for (Entity entity : getMembers()) {
            nodeList += getHostAndPort(entity) + ",";
        }
        if (!nodeList.isEmpty()) {
            for (Entity entity : getMembers()) {
                String otherNodesList = Strings.removeFromEnd(nodeList.replace(getHostAndPort(entity) + ",", ""), ",");
                if (!otherNodesList.isEmpty()) {
                    ((ElasticSearchNode)entity).resetCluster(otherNodesList);
                }
            }
            
        }
        setAttribute(NODE_LIST, Strings.removeFromEnd(nodeList, ","));
    }
    
    private String getHostAndPort(Entity entity) {
        return entity.getAttribute(Attributes.HOSTNAME) + ":" + entity.getAttribute(Attributes.HTTP_PORT);
    }
    
    public static class MemberTrackingPolicy extends AbstractMembershipTrackingPolicy {
        @Override protected void onEntityChange(Entity member) {
            ((ElasticSearchClusterImpl)entity).resetCluster();
        }
        @Override protected void onEntityAdded(Entity member) {
            ((ElasticSearchClusterImpl)entity).resetCluster();
        }
        @Override protected void onEntityRemoved(Entity member) {
            ((ElasticSearchClusterImpl)entity).resetCluster();
        }
    };
}
