package brooklyn.entity.nosql.riak;

import brooklyn.config.ConfigKey;
import brooklyn.entity.annotation.Effector;
import brooklyn.entity.annotation.EffectorParam;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.MethodEffector;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.Sensors;
import brooklyn.util.flags.SetFromFlag;

@ImplementedBy(RiakNodeImpl.class)
public interface RiakNode extends SoftwareProcess {

    @SetFromFlag("version")
    ConfigKey<String> SUGGESTED_VERSION = ConfigKeys.newConfigKeyWithDefault(SoftwareProcess.SUGGESTED_VERSION,
            "1.4.8");

    @SetFromFlag("riakVmArgsTemplateUrl")
    ConfigKey<String> RIAK_VM_ARGS_TEMPLATE_URL = ConfigKeys.newStringConfigKey(
            "riak.vm.args.template.url", "Template file (in freemarker format) for the vm.args config file",
            "classpath://brooklyn/entity/nosql/riak/vm.args");

    @SetFromFlag("riakNodeInCluster")
    AttributeSensor<Boolean> RIAK_NODE_IN_CLUSTER = Sensors.newBooleanSensor(
            "riak.node.in.cluster", "Flag to indicate wether the node is a cluster member");

    @SetFromFlag("riakNodeName")
    AttributeSensor<String> RIAK_NODE_NAME = Sensors.newStringSensor("riak.node.name","Returns the riak node name as defined in vm.args");

    public static final MethodEffector<Void> JOIN_RIAK_CLUSTER = new MethodEffector<Void>(RiakNode.class, "joinCluster");
    public static final MethodEffector<Void> LEAVE_RIAK_CLUSTER = new MethodEffector<Void>(RiakNode.class, "leaveCluster");

    @Effector(description = "add this riak node to a node already in the cluster")
    public void joinCluster(@EffectorParam(name = "riak.node.in.cluster") RiakNode node);

    @Effector(description = "remove this riak node from the cluster")
    public void leaveCluster();

}
