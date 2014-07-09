package brooklyn.entity.messaging.storm;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.entity.trait.Startable;
import brooklyn.util.flags.SetFromFlag;

@ImplementedBy(StormDeploymentImpl.class)
public interface StormDeployment extends Entity, Startable {

    @SetFromFlag("supervisors.count")
    ConfigKey<Integer> SUPERVISORS_COUNT = ConfigKeys.newConfigKey("storm.supervisors.count", "Number of supervisor nodes", 3);

    @SetFromFlag("zookeepers.count")
    ConfigKey<Integer> ZOOKEEPERS_COUNT = ConfigKeys.newConfigKey("storm.zookeepers.count", "Number of zookeeper nodes", 1);
    
}
