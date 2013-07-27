package brooklyn.entity.nosql.redis;

import brooklyn.catalog.Catalog;
import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.nosql.DataStore;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.event.basic.PortAttributeSensorAndConfigKey;
import brooklyn.event.basic.Sensors;
import brooklyn.util.flags.SetFromFlag;

/**
 * An entity that represents a Redis key-value store service.
 *
 * TODO add sensors with Redis statistics using INFO command
 */
@Catalog(name="Redis Server", description="Redis is an open-source, networked, in-memory, key-value data store with optional durability", iconUrl="classpath:///redis-logo.png")
@ImplementedBy(RedisStoreImpl.class)
public interface RedisStore extends SoftwareProcess, DataStore {

    @SetFromFlag("version")
    ConfigKey<String> SUGGESTED_VERSION =
            ConfigKeys.newConfigKeyWithDefault(SoftwareProcess.SUGGESTED_VERSION, "2.6.7");

    @SetFromFlag("downloadUrl")
    BasicAttributeSensorAndConfigKey<String> DOWNLOAD_URL = new BasicAttributeSensorAndConfigKey<String>(
            SoftwareProcess.DOWNLOAD_URL, "http://redis.googlecode.com/files/redis-${version}.tar.gz");

    @SetFromFlag("redisPort")
    PortAttributeSensorAndConfigKey REDIS_PORT = new PortAttributeSensorAndConfigKey("redis.port", "Redis port number", 6379);

    @SetFromFlag("redisConfigTemplateUrl")
    ConfigKey<String> REDIS_CONFIG_TEMPLATE_URL = ConfigKeys.newConfigKey(
            "redis.config.templateUrl", "Template file (in freemarker format) for the redis.conf config file", 
            "classpath://brooklyn/entity/nosql/redis/redis.conf");

    AttributeSensor<Integer> UPTIME = Sensors.newIntegerSensor("redis.uptime", "Redis uptime in seconds");

    // See http://redis.io/commands/info for details of all information available
    AttributeSensor<Integer> TOTAL_CONNECTIONS_RECEIVED = Sensors.newIntegerSensor("redis.connections.received.total", "Total number of connections accepted by the server");
    AttributeSensor<Integer> TOTAL_COMMANDS_PROCESSED = Sensors.newIntegerSensor("redis.commands.processed.total", "Total number of commands processed by the server");
    AttributeSensor<Integer> EXPIRED_KEYS = Sensors.newIntegerSensor("redis.keys.expired", "Total number of key expiration events");
    AttributeSensor<Integer> EVICTED_KEYS = Sensors.newIntegerSensor("redis.keys.evicted", "Number of evicted keys due to maxmemory limit");
    AttributeSensor<Integer> KEYSPACE_HITS = Sensors.newIntegerSensor("redis.keyspace.hits", "Number of successful lookup of keys in the main dictionary");
    AttributeSensor<Integer> KEYSPACE_MISSES = Sensors.newIntegerSensor("redis.keyspace.misses", "Number of failed lookup of keys in the main dictionary");

    String getAddress();

    Integer getRedisPort();

}
