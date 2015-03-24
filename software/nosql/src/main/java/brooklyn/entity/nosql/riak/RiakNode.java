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

import brooklyn.catalog.Catalog;
import brooklyn.config.ConfigKey;
import brooklyn.entity.annotation.Effector;
import brooklyn.entity.annotation.EffectorParam;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.MethodEffector;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.AttributeSensorAndConfigKey;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.event.basic.PortAttributeSensorAndConfigKey;
import brooklyn.event.basic.Sensors;
import brooklyn.event.basic.TemplatedStringAttributeSensorAndConfigKey;
import brooklyn.util.flags.SetFromFlag;

import com.google.common.collect.ImmutableList;
import com.google.common.reflect.TypeToken;

@Catalog(name="Riak Node", description="Riak is a distributed NoSQL key-value data store that offers "
        + "extremely high availability, fault tolerance, operational simplicity and scalability.")
@ImplementedBy(RiakNodeImpl.class)
public interface RiakNode extends SoftwareProcess {

    @SetFromFlag("version")
    ConfigKey<String> SUGGESTED_VERSION = ConfigKeys.newConfigKeyWithDefault(SoftwareProcess.SUGGESTED_VERSION,
            "Version to install (Default 2.0.5)", "2.0.5");

    @SetFromFlag("optimizeNetworking")
    ConfigKey<Boolean> OPTIMIZE_HOST_NETWORKING  = ConfigKeys.newBooleanConfigKey("riak.networking.optimize", "Optimize host networking when running in a VM", Boolean.TRUE);

    // vm.args and app.config are used for pre-version 2.0.0. Later versions use the (simplified) riak.conf
    // see https://github.com/joedevivo/ricon/blob/master/cuttlefish.md
    @SetFromFlag("vmArgsTemplateUrl")
    ConfigKey<String> RIAK_VM_ARGS_TEMPLATE_URL = ConfigKeys.newStringConfigKey(
            "riak.vmArgs.templateUrl", "Template file (in freemarker format) for the vm.args config file",
            "classpath://brooklyn/entity/nosql/riak/vm.args");
    @SetFromFlag("appConfigTemplateUrl")
    ConfigKey<String> RIAK_APP_CONFIG_TEMPLATE_URL = ConfigKeys.newStringConfigKey(
            "riak.appConfig.templateUrl", "Template file (in freemarker format) for the app.config config file",
            "classpath://brooklyn/entity/nosql/riak/app.config");
    @SetFromFlag("appConfigTemplateUrlLinux")
    ConfigKey<String> RIAK_CONF_TEMPLATE_URL_LINUX = ConfigKeys.newStringConfigKey(
            "riak.riakConf.templateUrl.linux", "Template file (in freemarker format) for the app.config config file",
            "classpath://brooklyn/entity/nosql/riak/riak.conf");
    @SetFromFlag("appConfigTemplateUrlMac")
    ConfigKey<String> RIAK_CONF_TEMPLATE_URL_MAC = ConfigKeys.newStringConfigKey(
            "riak.riakConf.templateUrl.mac", "Template file (in freemarker format) for the app.config config file",
            "classpath://brooklyn/entity/nosql/riak/riak-mac.conf");

    @SetFromFlag("downloadUrlRhelCentos")
    AttributeSensorAndConfigKey<String, String> DOWNLOAD_URL_RHEL_CENTOS = ConfigKeys.newTemplateSensorAndConfigKey("download.url.rhelcentos",
            "URL pattern for downloading the linux RPM installer (will substitute things like ${version} automatically)",
            "http://s3.amazonaws.com/downloads.basho.com/riak/${entity.majorVersion}/${entity.fullVersion}/rhel/" +
                    "${entity.osMajorVersion}/riak-${entity.fullVersion}-1.el${entity.osMajorVersion}.x86_64.rpm");

    @SetFromFlag("downloadUrlUbuntu")
    AttributeSensorAndConfigKey<String, String> DOWNLOAD_URL_UBUNTU = ConfigKeys.newTemplateSensorAndConfigKey("download.url.ubuntu",
            "URL pattern for downloading the linux Ubuntu installer (will substitute things like ${version} automatically)",
            "http://s3.amazonaws.com/downloads.basho.com/riak/${entity.majorVersion}/${entity.fullVersion}/ubuntu/" +
                    "$OS_RELEASE/riak_${entity.fullVersion}-1_amd64.deb");

    @SetFromFlag("downloadUrlDebian")
    AttributeSensorAndConfigKey<String, String> DOWNLOAD_URL_DEBIAN = ConfigKeys.newTemplateSensorAndConfigKey("download.url.debian",
            "URL pattern for downloading the linux Debian installer (will substitute things like ${version} automatically)",
            "http://s3.amazonaws.com/downloads.basho.com/riak/${entity.majorVersion}/${entity.fullVersion}/debian/" +
                    "$OS_RELEASE/riak_${entity.fullVersion}-1_amd64.deb");

    @SetFromFlag("downloadUrlMac")
    AttributeSensorAndConfigKey<String, String> DOWNLOAD_URL_MAC = ConfigKeys.newTemplateSensorAndConfigKey("download.url.mac",
            "URL pattern for downloading the MAC binaries tarball (will substitute things like ${version} automatically)",
            "http://s3.amazonaws.com/downloads.basho.com/riak/${entity.majorVersion}/${entity.fullVersion}/osx/10.8/riak-${entity.fullVersion}-OSX-x86_64.tar.gz");

    // NB these two needed for clients to access
    @SetFromFlag("riakWebPort")
    PortAttributeSensorAndConfigKey RIAK_WEB_PORT = new PortAttributeSensorAndConfigKey("riak.webPort", "Riak Web Port", "8098+");

    @SetFromFlag("riakPbPort")
    PortAttributeSensorAndConfigKey RIAK_PB_PORT = new PortAttributeSensorAndConfigKey("riak.pbPort", "Riak Protocol Buffers Port", "8087+");

    AttributeSensor<Boolean> RIAK_PACKAGE_INSTALL = Sensors.newBooleanSensor(
            "riak.install.package", "Flag to indicate whether Riak was installed using an OS package");
    AttributeSensor<Boolean> RIAK_ON_PATH = Sensors.newBooleanSensor(
            "riak.install.onPath", "Flag to indicate whether Riak is available on the PATH");

    AttributeSensor<Boolean> RIAK_NODE_HAS_JOINED_CLUSTER = Sensors.newBooleanSensor(
            "riak.node.riakNodeHasJoinedCluster", "Flag to indicate whether the Riak node has joined a cluster member");

    AttributeSensor<String> RIAK_NODE_NAME = Sensors.newStringSensor("riak.node", "Returns the riak node name as defined in vm.args");

    // these needed for nodes to talk to each other, but not clients (so ideally set up in the security group for internal access)
    PortAttributeSensorAndConfigKey HANDOFF_LISTENER_PORT = new PortAttributeSensorAndConfigKey("handoffListenerPort", "Handoff Listener Port", "8099+");
    PortAttributeSensorAndConfigKey EPMD_LISTENER_PORT = new PortAttributeSensorAndConfigKey("epmdListenerPort", "Erlang Port Mapper Daemon Listener Port", "4369");
    PortAttributeSensorAndConfigKey ERLANG_PORT_RANGE_START = new PortAttributeSensorAndConfigKey("erlangPortRangeStart", "Erlang Port Range Start", "6000+");
    PortAttributeSensorAndConfigKey ERLANG_PORT_RANGE_END = new PortAttributeSensorAndConfigKey("erlangPortRangeEnd", "Erlang Port Range End", "7999+");
    PortAttributeSensorAndConfigKey SEARCH_SOLR_PORT = new PortAttributeSensorAndConfigKey("search.solr.port", "Solr port", "8093+");
    PortAttributeSensorAndConfigKey SEARCH_SOLR_JMX_PORT = new PortAttributeSensorAndConfigKey("search.solr.jmx_port", "Solr port", "8985+");

    AttributeSensor<Integer> NODE_GETS = Sensors.newIntegerSensor("riak.node.gets");
    AttributeSensor<Integer> NODE_GETS_TOTAL = Sensors.newIntegerSensor("riak.node.gets.total");
    AttributeSensor<Integer> NODE_PUTS = Sensors.newIntegerSensor("riak.node.puts");
    AttributeSensor<Integer> NODE_PUTS_TOTAL = Sensors.newIntegerSensor("riak.node.puts.total");
    AttributeSensor<Integer> VNODE_GETS = Sensors.newIntegerSensor("riak.vnode.gets");
    AttributeSensor<Integer> VNODE_GETS_TOTAL = Sensors.newIntegerSensor("riak.vnode.gets.total");

    //Sensors for Riak Node Counters (within 1 minute window or lifetime of node.
    //http://docs.basho.com/riak/latest/ops/running/stats-and-monitoring/#Statistics-from-Riak
    AttributeSensor<Integer> VNODE_PUTS = Sensors.newIntegerSensor("riak.vnode.puts");
    AttributeSensor<Integer> VNODE_PUTS_TOTAL = Sensors.newIntegerSensor("riak.vnode.puts.total");
    AttributeSensor<Integer> READ_REPAIRS_TOTAL = Sensors.newIntegerSensor("riak.read.repairs.total");
    AttributeSensor<Integer> COORD_REDIRS_TOTAL = Sensors.newIntegerSensor("riak.coord.redirs.total");
    //Additional Riak node counters
    AttributeSensor<Integer> MEMORY_PROCESSES_USED = Sensors.newIntegerSensor("riak.memory.processes.used");
    AttributeSensor<Integer> SYS_PROCESS_COUNT = Sensors.newIntegerSensor("riak.sys.process.count");
    AttributeSensor<Integer> PBC_CONNECTS = Sensors.newIntegerSensor("riak.pbc.connects");
    AttributeSensor<Integer> PBC_ACTIVE = Sensors.newIntegerSensor("riak.pbc.active");
    @SuppressWarnings("serial")
    AttributeSensor<List<String>> RING_MEMBERS = Sensors.newSensor(new TypeToken<List<String>>() {},
            "ring.members", "all the riak nodes in the ring");

    MethodEffector<Void> JOIN_RIAK_CLUSTER = new MethodEffector<Void>(RiakNode.class, "joinCluster");
    MethodEffector<Void> LEAVE_RIAK_CLUSTER = new MethodEffector<Void>(RiakNode.class, "leaveCluster");
    MethodEffector<Void> COMMIT_RIAK_CLUSTER = new MethodEffector<Void>(RiakNode.class, "commitCluster");

    AttributeSensor<Integer> RIAK_NODE_GET_FSM_TIME_MEAN = Sensors.newIntegerSensor("riak.node_get_fsm_time_mean", "Time between reception of client read request and subsequent response to client");
    AttributeSensor<Integer> RIAK_NODE_PUT_FSM_TIME_MEAN = Sensors.newIntegerSensor("riak.node_put_fsm_time_mean", "Time between reception of client write request and subsequent response to client");
    AttributeSensor<Integer> RIAK_OBJECT_COUNTER_MERGE_TIME_MEAN = Sensors.newIntegerSensor("riak.object_counter_merge_time_mean", "Time it takes to perform an Update Counter operation");
    AttributeSensor<Integer> RIAK_OBJECT_SET_MERGE_TIME_MEAN = Sensors.newIntegerSensor("riak.object_set_merge_time_mean", "Time it takes to perform an Update Set operation");
    AttributeSensor<Integer> RIAK_OBJECT_MAP_MERGE_TIME_MEAN = Sensors.newIntegerSensor("riak.object_map_merge_time_mean", "Time it takes to perform an Update Map operation");
    AttributeSensor<Integer> RIAK_SEARCH_QUERY_LATENCY_MEDIAN = Sensors.newIntegerSensor("riak.search_query_latency_median", "Search query latency");
    AttributeSensor<Integer> RIAK_SEARCH_INDEX_LATENCY_MEDIAN = Sensors.newIntegerSensor("riak.search_index_latency_median", "Time it takes Search to index a new document");
    AttributeSensor<Integer> RIAK_CONSISTENT_GET_TIME_MEAN = Sensors.newIntegerSensor("riak.consistent_get_time_mean", "Strongly consistent read latency");
    AttributeSensor<Integer> RIAK_CONSISTENT_PUT_TIME_MEAN = Sensors.newIntegerSensor("riak.consistent_put_time_mean", "Strongly consistent write latency");

    List<AttributeSensor<Integer>> ONE_MINUTE_STRING_SENSORS = ImmutableList.of(RIAK_NODE_GET_FSM_TIME_MEAN, RIAK_NODE_PUT_FSM_TIME_MEAN,
            RIAK_OBJECT_COUNTER_MERGE_TIME_MEAN, RIAK_OBJECT_SET_MERGE_TIME_MEAN, RIAK_OBJECT_MAP_MERGE_TIME_MEAN,
            RIAK_SEARCH_QUERY_LATENCY_MEDIAN, RIAK_SEARCH_INDEX_LATENCY_MEDIAN,
            RIAK_CONSISTENT_GET_TIME_MEAN, RIAK_CONSISTENT_PUT_TIME_MEAN);

    ConfigKey<Boolean> DO_OS_TUNING = new BasicConfigKey<Boolean>(
            Boolean.class,
            "do.os.tunning",
            "Does sysctl OS optimizations. By default it is true",
            true);

    // accessors, for use from template file
    Integer getRiakWebPort();

    Integer getRiakPbPort();

    Integer getHandoffListenerPort();

    Integer getEpmdListenerPort();

    Integer getErlangPortRangeStart();

    Integer getErlangPortRangeEnd();

    Integer getSearchSolrPort();

    Integer getSearchSolrJmxPort();

    String getFullVersion();

    String getMajorVersion();

    String getOsMajorVersion();

    @Effector(description = "Add this riak node to the Riak cluster")
    public void joinCluster(@EffectorParam(name = "nodeName") String nodeName);

    @Effector(description = "Remove this Riak node from the cluster")
    public void leaveCluster(@EffectorParam(name = "nodeName") String nodeName);

    @Effector(description = "Recover a failed Riak node and join it back to the cluster (by passing it a working node on the cluster 'node')")
    public void recoverFailedNode(@EffectorParam(name = "nodeName") String nodeName);

    @Effector(description = "Commit changes made to a Riak cluster")
    public void commitCluster();

}
