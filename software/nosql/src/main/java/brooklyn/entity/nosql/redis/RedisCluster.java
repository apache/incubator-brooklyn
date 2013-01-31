package brooklyn.entity.nosql.redis;

import java.util.Collection;
import java.util.Map;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.basic.BasicConfigurableEntityFactory;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.group.DynamicClusterImpl;
import brooklyn.entity.trait.Startable;
import brooklyn.location.Location;
import brooklyn.util.MutableMap;

import com.google.common.collect.Maps;

/**
 * A cluster of {@link RedisStore}s with ione master and a group of slaves.
 *
 * The slaves are contained in a {@link DynamicCluster} which can be resized by a policy if required.
 *
 * TODO add sensors with aggregated Redis statistics from cluster
 */
public class RedisCluster extends AbstractEntity implements Startable {
    Map redisProperties = Maps.newLinkedHashMap();
    RedisCluster master;
    DynamicCluster slaves;

    public RedisCluster() {
        this(MutableMap.of(), null);
    }
    public RedisCluster(Map properties) {
        this(properties, null);
    }
    public RedisCluster(Entity parent) {
        this(MutableMap.of(), parent);
    }
    public RedisCluster(Map properties, Entity parent) {
        super(properties, parent);

        redisProperties.putAll(properties);
        redisProperties.put("factory", new BasicConfigurableEntityFactory(RedisSlave.class));
    }

    @Override
    public void start(Collection<? extends Location> locations) {
        master = new RedisCluster(redisProperties, this);
        master.start(locations);
        redisProperties.put("master", master);
        
        slaves = new DynamicClusterImpl(redisProperties, this);
        slaves.start(locations);
        
        setAttribute(Startable.SERVICE_UP, true);
    }

    @Override
    public void stop() {
        slaves.stop();
        master.stop();

        setAttribute(Startable.SERVICE_UP, false);
    }

    @Override
    public void restart() {
        throw new UnsupportedOperationException();
    }
}
