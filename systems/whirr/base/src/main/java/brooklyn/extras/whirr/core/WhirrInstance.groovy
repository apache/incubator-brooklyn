package brooklyn.extras.whirr.core

import org.apache.whirr.Cluster

import brooklyn.entity.Entity
import brooklyn.entity.basic.AbstractGroup
import brooklyn.entity.basic.Attributes
import brooklyn.entity.trait.Changeable
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.event.basic.BasicConfigKey
import brooklyn.util.flags.SetFromFlag

class WhirrInstance extends AbstractGroup {

    @SetFromFlag("role")
    public static final BasicConfigKey<String> ROLE =
        [String, "whirr.instance.role", "Apache Whirr instance role"]

    @SetFromFlag("instance")
    public static final BasicConfigKey<Cluster.Instance> INSTANCE =
        [Cluster.Instance, "whirr.instance.instance", "Apache Whirr instance Cluster.Instance"]
        
    public static final BasicAttributeSensor<String> HOSTNAME = Attributes.HOSTNAME;

    public WhirrInstance(Map<?,?> props, Entity parent) {
        super(props, parent);
        setAttribute(Changeable.GROUP_SIZE, 0);
        Cluster.Instance instance = getConfig(INSTANCE);
        if (instance) setAttribute(HOSTNAME, instance.publicHostName);
    }
        
    public WhirrInstance() {
        this(Collections.emptyMap(), null);
    }
        
    public WhirrInstance(Map<?,?> props) {
        this(props, null);
    }
        
    public WhirrInstance(Entity parent) {
        this(Collections.emptyMap(), parent);
    }
    
    public String getRole() {
        return getConfig(ROLE)
    }
}
