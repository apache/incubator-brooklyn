package brooklyn.entity.nosql.mongodb.sharding;

import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.proxying.ImplementedBy;

@ImplementedBy(MongoDBShardClusterImpl.class)
public interface MongoDBShardCluster extends DynamicCluster {

}
