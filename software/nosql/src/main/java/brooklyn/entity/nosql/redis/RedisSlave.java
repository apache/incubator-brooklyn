package brooklyn.entity.nosql.redis;

import java.util.Map;

import brooklyn.entity.Entity;
import brooklyn.util.MutableMap;

import com.google.common.base.Preconditions;

/**
 * A {@link RedisStore} configured as a slave.
 *
 * The {@code master} property must be set to the master Redis store entity.
 */
public class RedisSlave extends RedisStore {
    RedisStore master;

    public RedisSlave() {
        this(MutableMap.of(), null);
    }
    public RedisSlave(Map properties) {
        this(properties, null);
    }
    public RedisSlave(Entity parent) {
        this(MutableMap.of(), parent);
    }
    public RedisSlave(Map properties, Entity parent) {
        super(properties, parent);

        Preconditions.checkArgument(properties.containsKey("master"), "The Redis master entity must be specified");
        master = (RedisStore) properties.get("master");
    }

    @Override
    public String getConfigData(int port, boolean include) {
        String masterAddress = master.getAddress();
        int masterPort = getParent().getAttribute(REDIS_PORT);

        return super.getConfigData(port, include) + "slaveof "+masterAddress+" "+masterPort;
    }
}
