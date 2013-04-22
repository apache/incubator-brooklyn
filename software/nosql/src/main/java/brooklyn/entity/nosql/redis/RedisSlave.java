package brooklyn.entity.nosql.redis;

import brooklyn.entity.proxying.ImplementedBy;

/**
 * A {@link RedisStore} configured as a slave.
 *
 * The {@code master} property must be set to the master Redis store entity.
 */
@ImplementedBy(RedisSlaveImpl.class)
public interface RedisSlave extends RedisStore {
}
