package brooklyn.entity.nosql.redis;

import java.util.Map;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.nosql.Shard;
import brooklyn.util.MutableMap;

public class RedisShard extends AbstractEntity implements Shard {
    public RedisShard() {
        this(MutableMap.of(), null);
    }
    public RedisShard(Map properties) {
        this(properties, null);
    }
    public RedisShard(Entity owner) {
        this(MutableMap.of(), owner);
    }
    public RedisShard(Map properties, Entity owner) {
        super(properties, owner);
    }
}
