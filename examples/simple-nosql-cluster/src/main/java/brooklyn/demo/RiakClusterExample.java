package brooklyn.demo;

import brooklyn.catalog.Catalog;
import brooklyn.catalog.CatalogConfig;
import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.AbstractApplication;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.nosql.riak.RiakCluster;
import brooklyn.entity.nosql.riak.RiakNode;
import brooklyn.entity.proxying.EntitySpec;

@Catalog(name = "Riak Cluster Application", description = "Riak ring deployment blueprint")
public class RiakClusterExample extends AbstractApplication {

    @CatalogConfig(label = "Riak Ring Size")
    public static final ConfigKey<Integer> RIAK_RING_SIZE = ConfigKeys.newConfigKey(
            "riak.ring.size", "Initial size of the Riak Ring", 2);

    public void init() {

        addChild(EntitySpec.create(RiakCluster.class)
                .configure(RiakCluster.INITIAL_SIZE, getConfig(RIAK_RING_SIZE))
                .configure(RiakCluster.MEMBER_SPEC, EntitySpec.create(RiakNode.class)));
    }
}
