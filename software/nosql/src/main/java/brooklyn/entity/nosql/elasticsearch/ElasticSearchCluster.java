package brooklyn.entity.nosql.elasticsearch;

import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.util.flags.SetFromFlag;

/**
 * A cluster of {@link ElasticSearchNode}s based on {@link DynamicCluster} which can be resized by a policy if required.
 */
@ImplementedBy(ElasticSearchClusterImpl.class)
public interface ElasticSearchCluster extends DynamicCluster {
    @SetFromFlag("clusterName")
    BasicAttributeSensorAndConfigKey<String> CLUSTER_NAME = new BasicAttributeSensorAndConfigKey<String>(String.class, 
            "elasticsearch.cluster.name", "Name of the ElasticSearch cluster", "BrooklynCluster");
    
    String getClusterName();
}
