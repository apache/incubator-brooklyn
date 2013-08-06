package brooklyn.entity.nosql.redis;

import java.util.Collection;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.proxying.EntitySpecs;
import brooklyn.entity.trait.Startable;
import brooklyn.location.Location;

public class RedisClusterImpl extends AbstractEntity implements RedisCluster {

    protected RedisStore master;
    protected DynamicCluster slaves;

    public RedisClusterImpl() {
    }

    @Override
    public RedisStore getMaster() {
        return master;
    }
    
    @Override
    public DynamicCluster getSlaves() {
        return slaves;
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
