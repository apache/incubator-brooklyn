package brooklyn.entity.zookeeper;

import java.util.List;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.event.basic.Sensors;
import brooklyn.util.flags.SetFromFlag;

import com.google.common.reflect.TypeToken;

@ImplementedBy(ZooKeeperEnsembleImpl.class)
public interface ZooKeeperEnsemble extends DynamicCluster {

    @SetFromFlag("clusterName")
    BasicAttributeSensorAndConfigKey<String> CLUSTER_NAME = new BasicAttributeSensorAndConfigKey<String>(String
            .class, "zookeeper.cluster.name", "Name of the Zookeeper cluster", "BrooklynZookeeperCluster");

    @SetFromFlag("initialSize")
    public static final ConfigKey<Integer> INITIAL_SIZE = ConfigKeys.newConfigKeyWithDefault(DynamicCluster.INITIAL_SIZE, 3);

    @SuppressWarnings("serial")
    AttributeSensor<List<String>> ZOOKEEPER_SERVERS = Sensors.newSensor(new TypeToken<List<String>>() { },
            "zookeeper.servers", "Hostnames to connect to cluster with");

    String getClusterName();
}
