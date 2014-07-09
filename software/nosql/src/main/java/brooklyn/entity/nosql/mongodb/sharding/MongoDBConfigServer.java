package brooklyn.entity.nosql.mongodb.sharding;

import brooklyn.entity.nosql.mongodb.AbstractMongoDBServer;
import brooklyn.entity.proxying.ImplementedBy;

@ImplementedBy(MongoDBConfigServerImpl.class)
public interface MongoDBConfigServer extends AbstractMongoDBServer {

}
