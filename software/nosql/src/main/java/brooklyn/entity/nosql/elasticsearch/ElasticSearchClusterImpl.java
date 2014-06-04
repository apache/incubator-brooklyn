package brooklyn.entity.nosql.elasticsearch;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;

import brooklyn.entity.Entity;
import brooklyn.entity.group.DynamicClusterImpl;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.location.Location;

public class ElasticSearchClusterImpl extends DynamicClusterImpl implements ElasticSearchCluster {
    
    private AtomicInteger nextMemberId = new AtomicInteger(0);
    
    public ElasticSearchClusterImpl() {
        
    }
    
    @Override
    public void start(Collection<? extends Location> locations) {
        super.start(locations);
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
            .configure(ElasticSearchNode.NODE_NAME, "elasticsearch-" + nextMemberId.incrementAndGet());
        
        return spec;
    }
    
    @Override
    public String getName() {
        return getConfig(CLUSTER_NAME);
    }
    
}
