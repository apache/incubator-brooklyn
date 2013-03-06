package brooklyn.demo;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.nosql.redis.RedisCluster;
import brooklyn.entity.proxying.BasicEntitySpec;

/** Redis cluster. */
public class SimpleRedisCluster extends ApplicationBuilder {

    /** Create entities. */
    protected void doBuild() {
        createChild(BasicEntitySpec.newInstance(RedisCluster.class)
                .configure("initialSize", "2")
                .configure("clusterName", "Brooklyn"));
    }

}
