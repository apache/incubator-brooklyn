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

    RedisStore master;
    DynamicCluster slaves;

    public RedisClusterImpl() {
        this(MutableMap.of(), null);
    }
    public RedisClusterImpl(Map<?, ?> properties) {
        this(properties, null);
    }
    public RedisClusterImpl(Entity parent) {
        this(MutableMap.of(), parent);
    }
    public RedisClusterImpl(Map<?, ?> properties, Entity parent) {
        super(properties, parent);
    }

    @Override
    public void start(Collection<? extends Location> locations) {
        master = addChild(EntitySpecs.spec(RedisStore.class));
        Entities.manage(master);
        master.start(locations);

        slaves = addChild(EntitySpecs.spec(DynamicCluster.class)
                .configure(DynamicCluster.MEMBER_SPEC, EntitySpecs.spec(RedisSlave.class).configure(RedisSlave.MASTER, master)));
        slaves.start(locations);

        setAttribute(Startable.SERVICE_UP, calculateServiceUp());
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

    protected boolean calculateServiceUp() {
        boolean up = false;
        for (Entity member : slaves.getMembers()) {
            if (Boolean.TRUE.equals(member.getAttribute(SERVICE_UP))) up = true;
        }
        return up;
    }

}
