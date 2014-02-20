package brooklyn.entity.nosql.mongodb.sharding;

import java.util.Collection;

import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.Sensors;

@ImplementedBy(MongoDBRouterClusterImpl.class)
public interface MongoDBRouterCluster extends DynamicCluster {

    AttributeSensor<MongoDBRouter> ANY_ROUTER = Sensors.newSensor(MongoDBRouter.class, "mongodb.routercluster.any", 
            "When set, can be used to access one of the routers in the cluster (usually the first)");

    /**
     * @return One of the routers in the cluster if available, null otherwise
     */
    MongoDBRouter getAnyRouter();
    
    /**
     * @return All routers in the cluster
     */
    Collection<MongoDBRouter> getRouters();
}
