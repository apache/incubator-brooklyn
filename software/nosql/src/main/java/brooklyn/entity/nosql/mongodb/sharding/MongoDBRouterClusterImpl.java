package brooklyn.entity.nosql.mongodb.sharding;

import brooklyn.entity.group.DynamicClusterImpl;
import brooklyn.entity.proxying.EntitySpec;

public class MongoDBRouterClusterImpl extends DynamicClusterImpl implements MongoDBRouterCluster {

    @Override
    protected EntitySpec<?> getMemberSpec() {
        if (super.getMemberSpec() != null)
            return super.getMemberSpec();
        return EntitySpec.create(MongoDBRouter.class);
    }
    
}
