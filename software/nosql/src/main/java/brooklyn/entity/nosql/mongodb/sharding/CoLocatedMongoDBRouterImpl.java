package brooklyn.entity.nosql.mongodb.sharding;

import java.util.Collection;

import brooklyn.entity.basic.SameServerEntityImpl;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.trait.Startable;
import brooklyn.event.basic.DependentConfiguration;
import brooklyn.location.Location;

public class CoLocatedMongoDBRouterImpl extends SameServerEntityImpl implements CoLocatedMongoDBRouter {
    @Override
    public void init() {
        super.init();
        
        for (EntitySpec<?> siblingSpec : getConfig(CoLocatedMongoDBRouter.SIBLING_SPECS)) {
            addChild(siblingSpec);
        }
        setAttribute(ROUTER, addChild(EntitySpec.create(MongoDBRouter.class)
                .configure(MongoDBRouter.CONFIG_SERVERS,
                        DependentConfiguration.attributeWhenReady(getConfig(CoLocatedMongoDBRouter.SHARDED_DEPLOYMENT), MongoDBConfigServerCluster.CONFIG_SERVER_ADDRESSES))));
    }
    
    @Override
    public void start(Collection<? extends Location> locations) {
        super.start(locations);
        setAttribute(Startable.SERVICE_UP, true);
    }
}
