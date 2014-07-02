package brooklyn.demo;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.nosql.redis.RedisCluster;
import brooklyn.entity.proxying.EntitySpec;

/** Redis cluster. */
public class SimpleRedisCluster extends ApplicationBuilder {

    /** Create entities. */
    protected void doBuild() {
        addChild(EntitySpec.create(RedisCluster.class)
                .configure("initialSize", "2")
                .configure("clusterName", "Brooklyn"));
    }

}
