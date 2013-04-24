package brooklyn.entity.nosql.redis;

import java.util.Map;

import brooklyn.entity.Entity;
import brooklyn.util.MutableMap;

import com.google.common.base.Preconditions;

/**
 * A {@link RedisStore} configured as a slave.
 */
public class RedisSlaveImpl extends RedisStoreImpl implements RedisSlave {

    public RedisSlaveImpl() {
        this(MutableMap.of(), null);
    }
    public RedisSlaveImpl(Map<?, ?> properties) {
        this(properties, null);
    }
    public RedisSlaveImpl(Entity parent) {
        this(MutableMap.of(), parent);
    }
    public RedisSlaveImpl(Map<?, ?> properties, Entity parent) {
        super(properties, parent);
    }

    @Override
    public RedisStore getMaster() {
        return getConfig(MASTER);
    }
}
