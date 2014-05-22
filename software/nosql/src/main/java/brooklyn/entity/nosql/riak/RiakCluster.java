package brooklyn.entity.nosql.riak;

import java.util.Map;

import com.google.common.reflect.TypeToken;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.Sensors;
import brooklyn.util.flags.SetFromFlag;
import brooklyn.util.time.Duration;

@ImplementedBy(RiakClusterImpl.class)
public interface RiakCluster extends DynamicCluster {

    AttributeSensor<Map<Entity, String>> RIAK_CLUSTER_NODES = Sensors.newSensor(new TypeToken<Map<Entity, String>>() {
    }, "riak.cluster.nodes", "Names of all active Riak nodes in the cluster <Entity,Riak Name>");

    @SetFromFlag("delayBeforeAdvertisingCluster")
    ConfigKey<Duration> DELAY_BEFORE_ADVERTISING_CLUSTER = ConfigKeys.newConfigKey(Duration.class, "couchbase.cluster.delayBeforeAdvertisingCluster", "Delay after cluster is started before checking and advertising its availability", Duration.seconds(2 * 60));

}
