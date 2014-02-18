package brooklyn.entity.nosql.mongodb.sharding;

import brooklyn.entity.nosql.mongodb.AbstractMongoDBServer;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.util.time.Duration;

import com.google.common.reflect.TypeToken;

@ImplementedBy(MongoDBRouterImpl.class)
public interface MongoDBRouter extends AbstractMongoDBServer {

    @SuppressWarnings("serial")
    BasicConfigKey<Iterable<String>> CONFIG_SERVERS = new BasicConfigKey<Iterable<String>>(
            new TypeToken<Iterable<String>>(){}, "mongodb.router.config.servers", "List of host names and ports of the config servers");

    public void waitForServiceUp(Duration duration);
}
