package brooklyn.extras.whirr.core;

import org.apache.whirr.Cluster;

import brooklyn.entity.basic.AbstractGroup;
import brooklyn.entity.basic.Attributes;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.util.flags.SetFromFlag;

public interface WhirrInstance extends AbstractGroup {

    @SetFromFlag("role")
    public static final BasicConfigKey<String> ROLE = new BasicConfigKey<String>(
            String.class, "whirr.instance.role", "Apache Whirr instance role");

    @SetFromFlag("instance")
    public static final BasicConfigKey<Cluster.Instance> INSTANCE = new BasicConfigKey<Cluster.Instance>(
            Cluster.Instance.class, "whirr.instance.instance", "Apache Whirr instance Cluster.Instance");
        
    public static final BasicAttributeSensor<String> HOSTNAME = Attributes.HOSTNAME;

    public String getRole();
}
