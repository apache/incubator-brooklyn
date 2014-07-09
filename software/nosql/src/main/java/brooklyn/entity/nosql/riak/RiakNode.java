/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
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
import brooklyn.event.basic.PortAttributeSensorAndConfigKey;
import brooklyn.event.basic.Sensors;
import brooklyn.util.flags.SetFromFlag;

@ImplementedBy(RiakNodeImpl.class)
public interface RiakNode extends SoftwareProcess {

    @SetFromFlag("version")
    ConfigKey<String> SUGGESTED_VERSION = ConfigKeys.newConfigKeyWithDefault(SoftwareProcess.SUGGESTED_VERSION,
            "1.4.8");

    @SetFromFlag("vmArgsTemplateUrl")
    ConfigKey<String> RIAK_VM_ARGS_TEMPLATE_URL = ConfigKeys.newStringConfigKey(
            "riak.vmArgs.templateUrl", "Template file (in freemarker format) for the vm.args config file",
            "classpath://brooklyn/entity/nosql/riak/vm.args");
    @SetFromFlag("appConfigTemplateUrl")
    ConfigKey<String> RIAK_APP_CONFIG_TEMPLATE_URL = ConfigKeys.newStringConfigKey(
            "riak.appConfig.templateUrl", "Template file (in freemarker format) for the app.config config file",
            "classpath://brooklyn/entity/nosql/riak/app.config");

    @SetFromFlag("riakNodeHasJoinedCluster")
    AttributeSensor<Boolean> RIAK_NODE_HAS_JOINED_CLUSTER = Sensors.newBooleanSensor(
            "riak.node.riakNodeHasJoinedCluster", "Flag to indicate wether the Riak node has joined a cluster member");

    @SetFromFlag("riakNodeName")
    AttributeSensor<String> RIAK_NODE_NAME = Sensors.newStringSensor("riak.node", "Returns the riak node name as defined in vm.args");

    // NB these two needed for clients to access
    @SetFromFlag("riakWebPort")
    PortAttributeSensorAndConfigKey RIAK_WEB_PORT = new PortAttributeSensorAndConfigKey("riak.webPort", "Riak Web Port", "8098+");
    @SetFromFlag("riakPbPort")
    PortAttributeSensorAndConfigKey RIAK_PB_PORT = new PortAttributeSensorAndConfigKey("riak.pbPort", "Riak Protocol Buffers Port", "8087+");
    // these needed for nodes to talk to each other, but not clients (so ideally set up in the security group for internal access)
    PortAttributeSensorAndConfigKey HANDOFF_LISTENER_PORT = new PortAttributeSensorAndConfigKey("riak.handoffListenerPort", "Handoff Listener Port", "8099+");
    PortAttributeSensorAndConfigKey EPMD_LISTENER_PORT = new PortAttributeSensorAndConfigKey("riak.epmdListenerPort", "Erlang Port Mapper Daemon Listener Port", "4369");
    PortAttributeSensorAndConfigKey ERLANG_PORT_RANGE_START = new PortAttributeSensorAndConfigKey("riak.erlangPortRangeStart", "Erlang Port Range Start", "6000+");
    PortAttributeSensorAndConfigKey ERLANG_PORT_RANGE_END = new PortAttributeSensorAndConfigKey("riak.erlangPortRangeEnd", "Erlang Port Range End", "7999+");
    AttributeSensor<Integer> NODE_GETS = Sensors.newIntegerSensor("node.gets");
    AttributeSensor<Integer> NODE_GETS_TOTAL = Sensors.newIntegerSensor("node.gets.total");
    AttributeSensor<Integer> NODE_PUTS = Sensors.newIntegerSensor("node.puts");
    AttributeSensor<Integer> NODE_PUTS_TOTAL = Sensors.newIntegerSensor("node.puts.total");
    AttributeSensor<Integer> VNODE_GETS = Sensors.newIntegerSensor("vnode.gets");
    AttributeSensor<Integer> VNODE_GETS_TOTAL = Sensors.newIntegerSensor("vnode.gets.total");

    //Sensors for Riak Node Counters (within 1 minute window or lifetime of node.
    //http://docs.basho.com/riak/latest/ops/running/stats-and-monitoring/#Statistics-from-Riak
    AttributeSensor<Integer> VNODE_PUTS = Sensors.newIntegerSensor("vnode.puts");
    AttributeSensor<Integer> VNODE_PUTS_TOTAL = Sensors.newIntegerSensor("vnode.puts.total");
    AttributeSensor<Integer> READ_REPAIRS_TOTAL = Sensors.newIntegerSensor("read.repairs.total");
    AttributeSensor<Integer> COORD_REDIRS_TOTAL = Sensors.newIntegerSensor("coord.redirs.total");
    //Additional Riak node counters
    AttributeSensor<Integer> MEMORY_PROCESSES_USED = Sensors.newIntegerSensor("memory.processes.used");
    AttributeSensor<Integer> SYS_PROCESS_COUNT = Sensors.newIntegerSensor("sys.process.count");
    AttributeSensor<Integer> PBC_CONNECTS = Sensors.newIntegerSensor("pbc.connects");
    AttributeSensor<Integer> PBC_ACTIVE = Sensors.newIntegerSensor("pbc.active");
    @SuppressWarnings("serial")
    AttributeSensor<List<String>> RING_MEMBERS = Sensors.newSensor(new TypeToken<List<String>>() {
                                                                   },
            "ring.members", "all the riak nodes in the ring"
    );
    public static final MethodEffector<Void> JOIN_RIAK_CLUSTER = new MethodEffector<Void>(RiakNode.class, "joinCluster");
    public static final MethodEffector<Void> LEAVE_RIAK_CLUSTER = new MethodEffector<Void>(RiakNode.class, "leaveCluster");
    public static final MethodEffector<Void> COMMIT_RIAK_CLUSTER = new MethodEffector<Void>(RiakNode.class, "commitCluster");

    // accessors, for use from template file
    Integer getRiakWebPort();

    Integer getRiakPbPort();

    Integer getHandoffListenerPort();

    Integer getEpmdListenerPort();

    Integer getErlangPortRangeStart();

    Integer getErlangPortRangeEnd();

    @Effector(description = "add this riak node to the riak cluster")
    public void joinCluster(@EffectorParam(name = "nodeName") String nodeName);

    @Effector(description = "remove this riak node from the cluster")
    public void leaveCluster();

    @Effector(description = "recover a failed riak node and join it back to the cluster (by passing it a working node on the cluster 'node')")
    public void recoverFailedNode(@EffectorParam(name = "nodeName") String nodeName);

    @Effector(description = "commit changes made to a Riak cluster")
    public void commitCluster();

    public boolean hasJoinedCluster();
}
