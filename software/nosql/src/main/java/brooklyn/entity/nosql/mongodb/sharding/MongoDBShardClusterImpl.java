package brooklyn.entity.nosql.mongodb.sharding;

import brooklyn.entity.group.DynamicClusterImpl;
import brooklyn.entity.nosql.mongodb.MongoDBReplicaSet;
import brooklyn.entity.proxying.EntitySpec;

public class MongoDBShardClusterImpl extends DynamicClusterImpl implements MongoDBShardCluster {

    @Override
    protected EntitySpec<?> getMemberSpec() {
        EntitySpec<?> result = super.getMemberSpec(); 
        if (result == null)
            result = EntitySpec.create(MongoDBReplicaSet.class);
        result.configure(DynamicClusterImpl.INITIAL_SIZE, getConfig(MongoDBShardedDeployment.SHARD_REPLICASET_SIZE));
        return result;
    }
    
}
