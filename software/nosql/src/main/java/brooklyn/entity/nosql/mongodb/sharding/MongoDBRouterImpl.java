package brooklyn.entity.nosql.mongodb.sharding;

import brooklyn.entity.basic.SoftwareProcessImpl;

public class MongoDBRouterImpl extends SoftwareProcessImpl implements MongoDBRouter {

    @Override
    public Class<?> getDriverInterface() {
        return MongoDBRouterDriver.class;
    }

    @Override
    protected void connectSensors() {
        super.connectSensors();
        connectServiceUpIsRunning();
    }
}
