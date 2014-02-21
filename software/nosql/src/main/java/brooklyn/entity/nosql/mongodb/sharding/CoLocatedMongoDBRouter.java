package brooklyn.entity.nosql.mongodb.sharding;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.SameServerEntity;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.Sensors;
import brooklyn.util.flags.SetFromFlag;

import com.google.common.reflect.TypeToken;

@ImplementedBy(CoLocatedMongoDBRouterImpl.class)
public interface CoLocatedMongoDBRouter extends SameServerEntity {
    @SuppressWarnings("serial")
    @SetFromFlag("siblingSpecs")
    ConfigKey<Iterable<EntitySpec<?>>> SIBLING_SPECS = ConfigKeys.newConfigKey(new TypeToken<Iterable<EntitySpec<?>>>(){}, 
            "mongodb.colocatedrouter.sibling.specs", "Collection of (configured) specs for entities to be co-located with the router");
    
    @SetFromFlag("shardedDeployment")
    ConfigKey<MongoDBShardedDeployment> SHARDED_DEPLOYMENT = ConfigKeys.newConfigKey(MongoDBShardedDeployment.class, 
            "mongodb.colocatedrouter.shardeddeployment", "Sharded deployment to which the router should report");
    
    public static AttributeSensor<MongoDBRouter> ROUTER = Sensors.newSensor(MongoDBRouter.class, "mongodb.colocatedrouter.router",
            "Router");
}
