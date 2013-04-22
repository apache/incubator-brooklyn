package brooklyn.entity.nosql.redis;

import java.util.Map;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.util.MutableMap;

public class RedisShardImpl extends AbstractEntity implements RedisShard {
    public RedisShardImpl() {
        this(MutableMap.of(), null);
    }
    public RedisShardImpl(Map properties) {
        this(properties, null);
    }
    public RedisShardImpl(Entity parent) {
        this(MutableMap.of(), parent);
    }
    public RedisShardImpl(Map properties, Entity parent) {
        super(properties, parent);
    }
}
