package brooklyn.entity.nosql.mongodb.sharding;

import brooklyn.entity.nosql.mongodb.AbstractMongoDBServer;
import brooklyn.entity.proxying.ImplementedBy;

@ImplementedBy(MongoDBRouterImpl.class)
public interface MongoDBRouter extends AbstractMongoDBServer {

}
