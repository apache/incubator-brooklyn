package brooklyn.entity.nosql.mongodb.sharding;

import brooklyn.entity.basic.SoftwareProcessImpl;

public class MongoDBConfigServerImpl extends SoftwareProcessImpl implements MongoDBConfigServer {

    @Override
    public Class<?> getDriverInterface() {
        return MongoDBConfigServerDriver.class;
    }
    
    @Override
    protected void connectSensors() {
        super.connectSensors();
        connectServiceUpIsRunning();
    }

}
