package brooklyn.extras.whirr.core;

import org.apache.whirr.Cluster;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.AbstractGroup;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.util.flags.SetFromFlag;

@ImplementedBy(WhirrInstanceImpl.class)
public interface WhirrInstance extends AbstractGroup {

    @SetFromFlag("role")
    public static final ConfigKey<String> ROLE = ConfigKeys.newStringConfigKey(
            "whirr.instance.role", "Apache Whirr instance role", null);

    @SetFromFlag("instance")
    public static final ConfigKey<Cluster.Instance> INSTANCE = new BasicConfigKey<Cluster.Instance>(
            Cluster.Instance.class, "whirr.instance.instance", "Apache Whirr instance Cluster.Instance");
        
    public static final AttributeSensor<String> HOSTNAME = Attributes.HOSTNAME;

    public String getRole();
}
