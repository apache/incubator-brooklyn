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
    public RedisShard(Entity parent) {
        this(MutableMap.of(), parent);
    }
    public RedisShard(Map properties, Entity parent) {
        super(properties, parent);
    }
}
