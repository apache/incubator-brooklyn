package brooklyn.entity.nosql.mongodb.sharding;

import brooklyn.entity.basic.SameServerEntityImpl;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.event.basic.DependentConfiguration;

public class CoLocatedMongoDBRouterImpl extends SameServerEntityImpl implements CoLocatedMongoDBRouter {
    @Override
    public void init() {
        super.init();
        
        for (EntitySpec<?> siblingSpec : getConfig(CoLocatedMongoDBRouter.SIBLING_SPECS)) {
            addChild(siblingSpec);
        }
        setAttribute(ROUTER, addChild(EntitySpec.create(MongoDBRouter.class)
                .configure(MongoDBRouter.CONFIG_SERVERS,
                        DependentConfiguration.attributeWhenReady(getConfig(CoLocatedMongoDBRouter.SHARDED_DEPLOYMENT), MongoDBShardedDeployment.CONFIG_SERVER_ADDRESSES))));
    }
}
