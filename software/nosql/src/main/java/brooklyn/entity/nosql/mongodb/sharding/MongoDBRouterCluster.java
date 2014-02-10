package brooklyn.entity.nosql.mongodb.sharding;

import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.proxying.ImplementedBy;

@ImplementedBy(MongoDBRouterClusterImpl.class)
public interface MongoDBRouterCluster extends DynamicCluster {

}
