package brooklyn.entity.nosql.redis;

import brooklyn.catalog.Catalog;
import brooklyn.entity.Entity;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.entity.trait.Startable;

/**
 * A cluster of {@link RedisStore}s with one master and a group of slaves.
 *
 * The slaves are contained in a {@link DynamicCluster} which can be resized by a policy if required.
 *
 * TODO add sensors with aggregated Redis statistics from cluster
 */
@Catalog(name="Redis Cluster", description="Redis is an open-source, networked, in-memory, key-value data store with optional durability", iconUrl="classpath:///redis-logo.png")
@ImplementedBy(RedisClusterImpl.class)
public interface RedisCluster extends Entity, Startable {
    
    public RedisStore getMaster();
    
    public DynamicCluster getSlaves();
}
