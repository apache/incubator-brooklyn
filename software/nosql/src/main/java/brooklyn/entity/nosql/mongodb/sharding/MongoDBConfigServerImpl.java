package brooklyn.entity.nosql.mongodb.sharding;

import brooklyn.entity.basic.SoftwareProcessImpl;
import brooklyn.entity.nosql.mongodb.MongoDBDriver;

public class MongoDBConfigServerImpl extends SoftwareProcessImpl implements MongoDBConfigServer {

    @Override
    public Class<?> getDriverInterface() {
        return MongoDBDriver.class;
    }

}
