package brooklyn.entity.nosql.redis;

import brooklyn.catalog.Catalog;
import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.nosql.DataStore;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.event.basic.PortAttributeSensorAndConfigKey;
import brooklyn.util.flags.SetFromFlag;

/**
 * An entity that represents a Redis key-value store service.
 *
 * TODO add sensors with Redis statistics using INFO command
 */
@Catalog(name="Redis Server", description="Redis is an open-source, networked, in-memory, key-value data store with optional durability", iconUrl="classpath:///redis-logo.jpeg")
@ImplementedBy(RedisStoreImpl.class)
public interface RedisStore extends SoftwareProcess, DataStore {

    @SetFromFlag("version")
    public static final BasicConfigKey<String> SUGGESTED_VERSION =
            new BasicConfigKey<String>(SoftwareProcess.SUGGESTED_VERSION, "2.6.7");

    @SetFromFlag("downloadUrl")
    public static final BasicAttributeSensorAndConfigKey<String> DOWNLOAD_URL = new BasicAttributeSensorAndConfigKey<String>(
            SoftwareProcess.DOWNLOAD_URL, "http://redis.googlecode.com/files/redis-${version}.tar.gz");

    @SetFromFlag("redisPort")
    public static final PortAttributeSensorAndConfigKey REDIS_PORT = new PortAttributeSensorAndConfigKey("redis.port", "Redis port number", 6379);
    
    @SetFromFlag("configFile")
    public static final ConfigKey<String> REDIS_CONFIG_FILE = new BasicConfigKey<String>(String.class, "redis.config.file", "Redis user configuration file");
    
    public static final AttributeSensor<Integer> UPTIME = new BasicAttributeSensor<Integer>(Integer.class, "redis.uptime", "Redis uptime in seconds");

    public String getAddress();
    
    public String getConfigData(int port, boolean include);
}
