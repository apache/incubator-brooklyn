package brooklyn.entity.nosql.riak;

import java.util.List;

import com.google.common.reflect.TypeToken;

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

    @SetFromFlag("vmArgsTemplateUrl")
    ConfigKey<String> RIAK_VM_ARGS_TEMPLATE_URL = ConfigKeys.newStringConfigKey(
            "riak.vmargs.templateUrl", "Template file (in freemarker format) for the vm.args config file",
            "classpath://brooklyn/entity/nosql/riak/vm.args");

    @SetFromFlag("riakWebPort")
    ConfigKey<String> RIAK_WEB_PORT = ConfigKeys.newStringConfigKey("riak.webPort", "riak node's web port", "8098");

    @SetFromFlag("erlangInterNodePorts")
    ConfigKey<String> ERLANG_INTERNODE_PORTS = ConfigKeys.newStringConfigKey("erlang.interNodePorts", "range of tcp ports for internode erlang communication (set in app.config)", "6000-7999");

    @SetFromFlag("riakNodeInCluster")
    AttributeSensor<Boolean> RIAK_NODE_IN_CLUSTER = Sensors.newBooleanSensor(
            "riak.node.inCluster", "Flag to indicate wether the node is a cluster member");

    @SetFromFlag("riakNodeName")
    AttributeSensor<String> RIAK_NODE_NAME = Sensors.newStringSensor("riak.node", "Returns the riak node name as defined in vm.args");

    //Sensors for Riak Node Counters (within 1 minute window or lifetime of node.
    //http://docs.basho.com/riak/latest/ops/running/stats-and-monitoring/#Statistics-from-Riak

    AttributeSensor<Integer> NODE_GETS = Sensors.newIntegerSensor("node.gets");
    AttributeSensor<Integer> NODE_GETS_TOTAL = Sensors.newIntegerSensor("node.gets.total");
    AttributeSensor<Integer> NODE_PUTS = Sensors.newIntegerSensor("node.puts");
    AttributeSensor<Integer> NODE_PUTS_TOTAL = Sensors.newIntegerSensor("node.puts.total");
    AttributeSensor<Integer> VNODE_GETS = Sensors.newIntegerSensor("vnode.gets");
    AttributeSensor<Integer> VNODE_GETS_TOTAL = Sensors.newIntegerSensor("vnode.gets.total");
    AttributeSensor<Integer> VNODE_PUTS = Sensors.newIntegerSensor("vnode.puts");
    AttributeSensor<Integer> VNODE_PUTS_TOTAL = Sensors.newIntegerSensor("vnode.puts.total");

    AttributeSensor<Integer> READ_REPAIRS_TOTAL = Sensors.newIntegerSensor("read.repairs.total");
    AttributeSensor<Integer> COORD_REDIRS_TOTAL = Sensors.newIntegerSensor("coord.redirs.total");

    //Additional Riak node counters
    AttributeSensor<Integer> MEMORY_PROCESSES_USED = Sensors.newIntegerSensor("memory.processes.used");
    AttributeSensor<Integer> SYS_PROCESS_COUNT = Sensors.newIntegerSensor("sys.process.count");
    AttributeSensor<Integer> PBC_CONNECTS = Sensors.newIntegerSensor("pbc.connects");
    AttributeSensor<Integer> PBC_ACTIVE = Sensors.newIntegerSensor("pbc.active");

    AttributeSensor<List<String>> RING_MEMBERS = Sensors.newSensor(new TypeToken<List<String>>() {
    }, "ring.members", "all the riak nodes in the ring");


    public static final MethodEffector<Void> JOIN_RIAK_CLUSTER = new MethodEffector<Void>(RiakNode.class, "joinCluster");
    public static final MethodEffector<Void> LEAVE_RIAK_CLUSTER = new MethodEffector<Void>(RiakNode.class, "leaveCluster");

    @Effector(description = "add this riak node to the riak cluster")
    public void joinCluster(@EffectorParam(name = "nodeName") String nodeName);

    @Effector(description = "remove this riak node from the cluster")
    public void leaveCluster();

    @Effector(description = "recover a failed riak node and join it back to the cluster (by passing it a working node on the cluster 'node')")
    public void recoverFailedNode(@EffectorParam(name = "nodeName") String nodeName);

}
