package brooklyn.entity.nosql.redis;

import java.util.Collection;
import java.util.Map;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.basic.BasicConfigurableEntityFactory;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.proxying.EntitySpecs;
import brooklyn.entity.trait.Startable;
import brooklyn.location.Location;
import brooklyn.util.MutableMap;

import com.google.common.collect.Maps;

public class RedisClusterImpl extends AbstractEntity implements RedisCluster {
    Map redisProperties = Maps.newLinkedHashMap();
    RedisStore master;
    DynamicCluster slaves;

    public RedisClusterImpl() {
        this(MutableMap.of(), null);
    }
    public RedisClusterImpl(Map properties) {
        this(properties, null);
    }
    public RedisClusterImpl(Entity parent) {
        this(MutableMap.of(), parent);
    }
    public RedisClusterImpl(Map properties, Entity parent) {
        super(properties, parent);

        redisProperties.putAll(properties);
    }

    @Override
    public void start(Collection<? extends Location> locations) {
        master = addChild(EntitySpecs.spec(RedisStore.class)
                .configure(redisProperties));
        Entities.manage(master);
        master.start(locations);
        redisProperties.put("master", master);
        
        slaves = addChild(EntitySpecs.spec(DynamicCluster.class)
                .configure(redisProperties)
                .configure(DynamicCluster.FACTORY, new BasicConfigurableEntityFactory(RedisSlave.class)));
        slaves.start(locations);
        
        setAttribute(Startable.SERVICE_UP, true);
    }

    @Override
    public void stop() {
        if (slaves != null) slaves.stop();
        if (master != null) master.stop();

        setAttribute(Startable.SERVICE_UP, false);
    }

    @Override
    public void restart() {
        throw new UnsupportedOperationException();
    }
}
