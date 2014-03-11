package brooklyn.entity.nosql.mongodb;

import brooklyn.entity.basic.SoftwareProcessImpl;
import brooklyn.entity.trait.Startable;

public class MongoDBClientImpl extends SoftwareProcessImpl implements MongoDBClient {
    
    @Override
    protected void connectSensors() {
        super.connectSensors();
        setAttribute(Startable.SERVICE_UP, true);
    }
    
    @Override
    protected void connectServiceUpIsRunning() {
        // NO-OP
    }

    @SuppressWarnings("rawtypes")
    @Override
    public Class getDriverInterface() {
        return MongoDBClientDriver.class;
    }

    @Override
    public void runScript(String scriptName) {
        ((MongoDBClientDriver)getDriver()).runScript(scriptName);
    }

}
