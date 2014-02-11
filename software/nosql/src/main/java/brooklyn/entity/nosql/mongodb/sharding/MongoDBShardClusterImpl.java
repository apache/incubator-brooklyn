package brooklyn.entity.nosql.mongodb.sharding;

import brooklyn.entity.group.DynamicClusterImpl;
import brooklyn.entity.nosql.mongodb.MongoDBReplicaSet;
import brooklyn.entity.proxying.EntitySpec;

public class MongoDBShardClusterImpl extends DynamicClusterImpl implements MongoDBShardCluster {

    @Override
    protected EntitySpec<?> getMemberSpec() {
        if (super.getMemberSpec() != null)
            return super.getMemberSpec();
        return EntitySpec.create(MongoDBReplicaSet.class);
    }
    
}
