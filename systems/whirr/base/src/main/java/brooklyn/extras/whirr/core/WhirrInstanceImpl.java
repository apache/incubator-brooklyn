package brooklyn.extras.whirr.core;

import java.io.IOException;

import org.apache.whirr.Cluster;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.AbstractGroupImpl;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.trait.Changeable;
import brooklyn.event.AttributeSensor;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.flags.SetFromFlag;

public class WhirrInstanceImpl extends AbstractGroupImpl implements WhirrInstance {

    @SetFromFlag("role")
    public static final ConfigKey<String> ROLE = ConfigKeys.newStringConfigKey("whirr.instance.role", "Apache Whirr instance role");

    @SetFromFlag("instance")
    public static final ConfigKey<Cluster.Instance> INSTANCE = ConfigKeys.newConfigKey(Cluster.Instance.class, "whirr.instance.instance", "Apache Whirr instance Cluster.Instance");
        
    public static final AttributeSensor<String> HOSTNAME = Attributes.HOSTNAME;

    public WhirrInstanceImpl() {
        super();
    }
    
    @Override
    public void init() {
        setAttribute(Changeable.GROUP_SIZE, 0);
        Cluster.Instance instance = getConfig(INSTANCE);
        if (instance != null) {
            try {
                setAttribute(HOSTNAME, instance.getPublicHostName());
            } catch (IOException e) {
                throw Exceptions.propagate(e);
            }
        }
    }
    
    @Override
    public String getRole() {
        return getConfig(ROLE);
    }
}
