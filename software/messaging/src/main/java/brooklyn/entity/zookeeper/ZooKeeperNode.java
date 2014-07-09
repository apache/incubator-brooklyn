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
package brooklyn.entity.zookeeper;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.event.basic.PortAttributeSensorAndConfigKey;
import brooklyn.util.flags.SetFromFlag;

/**
 * An {@link brooklyn.entity.Entity} that represents a single Apache ZooKeeper instance.
 */
@ImplementedBy(ZooKeeperNodeImpl.class)
public interface ZooKeeperNode extends SoftwareProcess {

    @SetFromFlag("version")
    ConfigKey<String> SUGGESTED_VERSION = ConfigKeys.newConfigKeyWithDefault(SoftwareProcess.SUGGESTED_VERSION, "3.4.5");
    @SetFromFlag("zookeeperPort")
    PortAttributeSensorAndConfigKey ZOOKEEPER_PORT = new PortAttributeSensorAndConfigKey("zookeeper.port", "Zookeeper port", "2181+");
    @SetFromFlag("zookeeperLeaderPort")
    PortAttributeSensorAndConfigKey ZOOKEEPER_LEADER_PORT = new PortAttributeSensorAndConfigKey("zookeeper.leader.port", "Zookeeper leader ports", "2888+");
    @SetFromFlag("zookeeperElectionPort")
    PortAttributeSensorAndConfigKey ZOOKEEPER_ELECTION_PORT = new PortAttributeSensorAndConfigKey("zookeeper.election.port", "Zookeeper election ports", "3888+");
    @SetFromFlag("downloadUrl")
    BasicAttributeSensorAndConfigKey<String> DOWNLOAD_URL = new BasicAttributeSensorAndConfigKey<String>(
            SoftwareProcess.DOWNLOAD_URL, "http://apache.fastbull.org/zookeeper/zookeeper-${version}/zookeeper-${version}.tar.gz");
    /**
     * Location of the ZK configuration file template to be copied to the server.
     */
    @SetFromFlag("zookeeperConfig")
    ConfigKey<String> ZOOKEEPER_CONFIG_TEMPLATE = ConfigKeys.newStringConfigKey(
            "zookeeper.configTemplate", "Zookeeper configuration template (in freemarker format)",
            "classpath://brooklyn/entity/messaging/zookeeper/zoo.cfg");
    AttributeSensor<Long> OUTSTANDING_REQUESTS = new BasicAttributeSensor<Long>(Long.class, "zookeeper.outstandingRequests", "Outstanding request count");
    AttributeSensor<Long> PACKETS_RECEIVED = new BasicAttributeSensor<Long>(Long.class, "zookeeper.packets.received", "Total packets received");
    AttributeSensor<Long> PACKETS_SENT = new BasicAttributeSensor<Long>(Long.class, "zookeeper.packets.sent", "Total packets sent");
    AttributeSensor<Integer> MY_ID = new BasicAttributeSensor<Integer>(Integer.class, "zookeeper.myid", "ZooKeeper node's myId");

    Integer getZookeeperPort();

    String getHostname();
}
