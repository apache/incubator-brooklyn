package brooklyn.entity.nosql.mongodb.sharding;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.nosql.mongodb.AbstractMongoDBServer;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.util.time.Duration;

import com.google.common.reflect.TypeToken;

@ImplementedBy(MongoDBRouterImpl.class)
public interface MongoDBRouter extends AbstractMongoDBServer {

    @SuppressWarnings("serial")
    ConfigKey<Iterable<String>> CONFIG_SERVERS = ConfigKeys.newConfigKey(
            new TypeToken<Iterable<String>>(){}, "mongodb.router.config.servers", "List of host names and ports of the config servers");

    public void waitForServiceUp(Duration duration);
}
