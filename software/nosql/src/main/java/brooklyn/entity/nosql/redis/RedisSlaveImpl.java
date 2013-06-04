package brooklyn.entity.nosql.redis;


/**
 * A {@link RedisStore} configured as a slave.
 */
public class RedisSlaveImpl extends RedisStoreImpl implements RedisSlave {

    public RedisSlaveImpl() {
    }

    @Override
    public RedisStore getMaster() {
        return getConfig(MASTER);
    }
}
