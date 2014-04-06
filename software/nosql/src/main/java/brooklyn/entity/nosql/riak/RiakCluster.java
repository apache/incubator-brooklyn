package brooklyn.entity.nosql.riak;

import brooklyn.entity.Entity;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.Sensors;
import com.google.common.reflect.TypeToken;

import java.util.Map;

@ImplementedBy(RiakClusterImpl.class)
public interface RiakCluster extends DynamicCluster {

    AttributeSensor<Map<Entity, String>> RIAK_CLUSTER_NODES = Sensors.newSensor(new TypeToken<Map<Entity, String>>() {
    }, "sge.cluster.nodes", "Names of all active Riak nodes in the cluster <Entity,Riak Name>");
}
