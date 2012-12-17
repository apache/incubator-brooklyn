package brooklyn.entity.nosql.mongodb;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.SoftwareProcessEntity;
import brooklyn.event.adapter.FunctionSensorAdapter;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.event.basic.PortAttributeSensorAndConfigKey;
import brooklyn.util.flags.SetFromFlag;
import com.google.common.collect.ImmutableMap;

import java.util.Map;
import java.util.concurrent.Callable;

public class MongoDbServer extends SoftwareProcessEntity {

    @SetFromFlag("port")
    public static final PortAttributeSensorAndConfigKey PORT =
            new PortAttributeSensorAndConfigKey("mongo.server.port", "Server port", "27017+");

    public static final BasicConfigKey<String> VERSION = new BasicConfigKey<String>(String.class,
            "mongo.version", "Required version of Mongo", "2.2.2");

    public static final BasicConfigKey<String> CONFIG_URL = new BasicConfigKey<String>(String.class,
            "mongo.config.url", "URL where a Mongo configuration file can be found", "classpath://default-mongodb.conf");

    public MongoDbServer(Map flags){
        this(flags, null);
    }

    public MongoDbServer(Map flags, Entity owner) {
        super(flags, owner);
    }

    @Override
    public Class getDriverInterface() {
        return MongoDbDriver.class;
    }

    @Override
    protected void connectSensors() {
        FunctionSensorAdapter serviceUpAdapter = sensorRegistry.register(new FunctionSensorAdapter(
                ImmutableMap.of("period", 10 * 1000),
                new Callable<Boolean>() {
                    public Boolean call() {
                        return getDriver().isRunning();
                    }
                }));
        serviceUpAdapter.poll(SERVICE_UP);
    }


}
