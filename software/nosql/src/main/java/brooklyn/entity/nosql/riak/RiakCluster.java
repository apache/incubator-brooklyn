package brooklyn.entity.nosql.riak;

import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.proxying.ImplementedBy;

@ImplementedBy(RiakClusterImpl.class)
public interface RiakCluster extends DynamicCluster {
}
