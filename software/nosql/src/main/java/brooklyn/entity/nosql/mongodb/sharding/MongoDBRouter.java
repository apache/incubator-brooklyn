package brooklyn.entity.nosql.mongodb.sharding;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.nosql.mongodb.AbstractMongoDBServer;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.Sensors;
import brooklyn.util.time.Duration;

import com.google.common.reflect.TypeToken;

@ImplementedBy(MongoDBRouterImpl.class)
public interface MongoDBRouter extends AbstractMongoDBServer {

    @SuppressWarnings("serial")
    ConfigKey<Iterable<String>> CONFIG_SERVERS = ConfigKeys.newConfigKey(
            new TypeToken<Iterable<String>>(){}, "mongodb.router.config.servers", "List of host names and ports of the config servers");
    
    AttributeSensor<Integer> SHARD_COUNT = Sensors.newIntegerSensor("mongodb.router.config.shard.count", "Number of shards that have been added");
    
    AttributeSensor<Boolean> RUNNING = Sensors.newBooleanSensor("mongodb.router.running", "Indicates that the router is running, "
            + "and can be used to add shards, but is not necessarity available for CRUD operations (e.g. if no shards have been added)");

    public void waitForServiceUp(Duration duration);
}
