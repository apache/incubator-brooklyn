package brooklyn.entity.nosql.redis;

import brooklyn.entity.Entity;
import brooklyn.entity.nosql.Shard;
import brooklyn.entity.proxying.ImplementedBy;

@ImplementedBy(RedisShardImpl.class)
public interface RedisShard extends Entity, Shard {
}
