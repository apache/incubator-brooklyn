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
package org.apache.brooklyn.entity.zookeeper;

import org.apache.brooklyn.api.catalog.Catalog;
import org.apache.brooklyn.api.entity.ImplementedBy;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.sensor.core.BasicAttributeSensor;
import org.apache.brooklyn.sensor.core.BasicAttributeSensorAndConfigKey;
import org.apache.brooklyn.sensor.core.PortAttributeSensorAndConfigKey;
import org.apache.brooklyn.util.core.flags.SetFromFlag;

import brooklyn.entity.basic.SoftwareProcess;

/**
 * An {@link org.apache.brooklyn.api.entity.Entity} that represents a single Apache ZooKeeper instance.
 */
@Catalog(name="ZooKeeper Node", description="Apache ZooKeeper is a server which enables "
        + "highly reliable distributed coordination.")
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
            "classpath://org/apache/brooklyn/entity/messaging/zookeeper/zoo.cfg");
    AttributeSensor<Long> OUTSTANDING_REQUESTS = new BasicAttributeSensor<Long>(Long.class, "zookeeper.outstandingRequests", "Outstanding request count");
    AttributeSensor<Long> PACKETS_RECEIVED = new BasicAttributeSensor<Long>(Long.class, "zookeeper.packets.received", "Total packets received");
    AttributeSensor<Long> PACKETS_SENT = new BasicAttributeSensor<Long>(Long.class, "zookeeper.packets.sent", "Total packets sent");
    AttributeSensor<Integer> MY_ID = new BasicAttributeSensor<Integer>(Integer.class, "zookeeper.myid", "ZooKeeper node's myId");

    Integer getZookeeperPort();

    String getHostname();
}
