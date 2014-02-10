package brooklyn.entity.nosql.mongodb.sharding;

import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.proxying.ImplementedBy;

@ImplementedBy(MongoDBConfigServerClusterImpl.class)
public interface MongoDBConfigServerCluster extends DynamicCluster {

}
