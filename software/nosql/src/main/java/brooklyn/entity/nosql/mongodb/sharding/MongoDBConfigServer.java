package brooklyn.entity.nosql.mongodb.sharding;

import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.nosql.mongodb.AbstractMongoDBServer;
import brooklyn.entity.proxying.ImplementedBy;

@ImplementedBy(MongoDBConfigServerImpl.class)
public interface MongoDBConfigServer extends SoftwareProcess, AbstractMongoDBServer {

}
