package brooklyn.demo;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.nosql.riak.RiakCluster;
import brooklyn.entity.nosql.riak.RiakNode;
import brooklyn.entity.proxying.EntitySpec;

public class RiakClusterExample extends ApplicationBuilder {

    protected void doBuild() {

        addChild(EntitySpec.create(RiakCluster.class)
                .configure(RiakCluster.INITIAL_SIZE, 3)
                .configure(RiakCluster.MEMBER_SPEC, EntitySpec.create(RiakNode.class)));
    }
}
